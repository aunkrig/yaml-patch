
/*
 * yaml-patch - A command-line tool for modifying YAML documents
 *
 * Copyright (c) 2023, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.zz.yamlpatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.common.FlowStyle;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.protocol.Transformer;
import de.unkrig.commons.nullanalysis.NotNull;
import de.unkrig.commons.nullanalysis.Nullable;

public
class YamlPatch {

    static {
        AssertionUtil.enableAssertionsForThisClass();
    }

    private FlowStyle                               flowStyle = FlowStyle.AUTO;
    private boolean                                 keepOriginals;
    private final List<Transformer<Object, Object>> documentModifiers = new ArrayList<>();

    public void
    setFlowStyle(FlowStyle value) { this.flowStyle = value; }

    public void
    setKeepOriginals(boolean value) { this.keepOriginals = value; }

    public void
    addSet(String spec, Object value, SetMode mode) throws IOException {
        this.documentModifiers.add(root -> this.set(root, spec, value, mode));
    }
    public static enum SetMode { ANY, EXISTING, NON_EXISTING }
    
    public void
    addRemove(String spec, RemoveMode mode) throws IOException {
        this.documentModifiers.add(root -> this.remove(root, spec, mode));
    }
    public static enum RemoveMode { ANY, EXISTING }

    public void
    addInsert(String spec, Object sequenceElement) throws IOException {
        this.documentModifiers.add(root -> this.insert(root, spec, sequenceElement));
    }
    
    public void
    addAdd(String spec) throws IOException {
        this.documentModifiers.add(root -> this.add(root, spec));
    }

    /**
     * Adds or changes one sequence element or map entry.
     * 
     * @throws IndexOutOfBoundsException <var>mode</var> is {@code EXISTING}, and the specified sequence index is out of
     *                                   range
     * @throws IndexOutOfBoundsException <var>mode</var> is {@code NON_EXISTING}, and the specified sequence index does not
     *                                   equal the sequence size
     * @throws IllegalArgumentException  <var>mode</var> is {@code EXISTING}, and the specified map entry does not
     *                                   exist
     * @throws IllegalArgumentException  <var>mode</var> is {@code NON_EXISTING}, and the specified map entry does
     *                                   exist
     * @throws IllegalArgumentException  The designated node is a set (use {@link #add(Object, String)} instead)
     */
    public Object
    set(Object root, String spec, Object value, SetMode mode) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            
            @Override public void
            handleMapEntry(Map<Object, Object> map, Object key) {
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (!map.containsKey(key)) throw new IllegalArgumentException("Entry key \"" + key + "\" does not exist");
                    break;
                case NON_EXISTING:
                    if (map.containsKey(key)) throw new IllegalArgumentException("Entry key \"" + key + "\" already exists");
                    break;
                }
                map.put(key, value);
            }

            @Override public void
            handleSequenceElement(List<Object> sequence, int index) {
                if (index < 0) index += sequence.size();
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (index >= sequence.size()) throw new IndexOutOfBoundsException("Index " + index + " too large");
                    break;
                case NON_EXISTING:
                    if (index != sequence.size()) throw new IndexOutOfBoundsException("Index " + index + " not equal to sequence size");
                    break;
                }
                if (index == sequence.size()) {
                    sequence.add(value);
                } else {
                    sequence.set(index, value);
                }
            }

            @Override public void
            handleSetMember(Set<Object> set, Object member) {
                throw new IllegalArgumentException("Cannot \"set\"; use \"add\" for yaml sets");
            }
        });
        
        return root;
    }

    /**
     * Removes one sequence element, map entry or set member.
     * 
     * @param mode                       (Irrelevant if an sequence element is specified)
     * @throws IndexOutOfBoundsException The specified sequence index is out of range (-sequenceSize ... sequenceSize-1)
     * @throws IllegalArgumentException  <var>mode</var> is {@code EXISTING}, and the specified map key does not
     *                                   exist
     */
    public Object
    remove(Object root, String spec, RemoveMode mode) {
        
        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(Map<Object, Object> map, Object key) {
                if (map.remove(key) == null && mode == RemoveMode.EXISTING) throw new IllegalArgumentException("Key \"" + key + "\" does not exist");
            }
            
            @Override public void
            handleSequenceElement(List<Object> sequence, int index) {
                if (index < 0) index += sequence.size();
                sequence.remove(index);
            }

            @Override public void
            handleSetMember(Set<Object> set, Object member) {
                if (!set.remove(member) && mode == RemoveMode.EXISTING) throw new IllegalArgumentException("Member \"" + member + "\" does not exist");
            }
        });

        return root;
    }

    /**
     * Inserts an element into a sequence.
     *
     * @throws IndexOutOfBoundsException The specified sequence index is out of range (-sequenceSize ... sequenceSize)
     * @throws IllegalArgumentException  The <var>spec</var> specified an map or a set (and not an sequence)
     */
    public Object
    insert(Object root, String spec, Object sequenceElement) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(Map<Object, Object> map, Object key) {
                throw new IllegalArgumentException("Cannot insert into map; use SET instead");
            }

            @Override public void
            handleSequenceElement(List<Object> sequence, int index) {
                sequence.add(index, sequenceElement);
            }

            @Override public void
            handleSetMember(Set<Object> set, Object member) {
                throw new IllegalArgumentException("Cannot insert into set; use ADD instead");
            }
        });
        return root;
    }

    /**
     * Adds an element to a sequence, or a member to a set.
     *
     * @throws IndexOutOfBoundsException The specified sequence index is out of range (-sequenceSize ... sequenceSize)
     * @throws IllegalArgumentException  The <var>spec</var> specified a map (and not a sequence or a set)
     */
    public Object
    add(Object root, String spec) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(Map<Object, Object> map, Object key) {
                throw new IllegalArgumentException("Cannot add to map; use SET instead");
            }

            @Override public void
            handleSequenceElement(List<Object> sequence, int index) {
                throw new IllegalArgumentException("Cannot add to sequence; use INSERT instead");
            }

            @Override public void
            handleSetMember(Set<Object> set, Object member) {
                set.add(member);
            }
        });
        return root;
    }

    public
    interface SpecHandler {

        /**
         * Designated node is a map.
         */
        void handleMapEntry(Map<Object, Object> map, Object key);

        /**
         * Designated node is a sequence.
         */
        void handleSequenceElement(List<Object> sequence, int index);
        
        /**
         * Designated node is a set.
         */
        void handleSetMember(Set<Object> set, Object member);
    }

    private static final Pattern MAP_ENTRY_SPEC1       = Pattern.compile("\\.([A-Za-z0-9_]+)");
    private static final Pattern MAP_ENTRY_SPEC2       = Pattern.compile("\\.\\((.*?)\\)");
    private static final Pattern SEQUENCE_ELEMENT_SPEC = Pattern.compile("\\[(-?\\d*)]");
    private static final Pattern SET_MEMBER_SPEC       = Pattern.compile("\\{(.*?)\\}$");

    private static void
    processSpec(Object root, String spec, SpecHandler specHandler) {

        Object el = root;
        Matcher m;
        for (String s = spec;; s = s.substring(m.end())) {
            try {

                if (
                    (m = MAP_ENTRY_SPEC1.matcher(s)).lookingAt()     // .<identifier>
                    || (m = MAP_ENTRY_SPEC2.matcher(s)).lookingAt()  // .(<yaml-document>)
                ) {
                    
                    if (!(el instanceof Map)) throw new IllegalArgumentException("Element is not a map");
                    @SuppressWarnings("unchecked") Map<Object, Object> yamlMap = (Map<Object, Object>) el;

                    Object key = YamlPatch.loadYaml(new StringReader(m.group(1)));

                    if (m.end() == s.length()) {
                        specHandler.handleMapEntry(yamlMap, key);
                        return;
                    }

                    if (!yamlMap.containsKey(key)) throw new IllegalArgumentException("Map does not contain key \"" + key + "\"");
                    el = yamlMap.get(key);
                } else
                if ((m = SEQUENCE_ELEMENT_SPEC.matcher(s)).lookingAt()) {  // [<integer>]

                    if (!(el instanceof List)) throw new IllegalArgumentException("Element is not a sequence");
                    @SuppressWarnings("unchecked") @NotNull List<Object> yamlSequence = (List<Object>) el;

                    int index = m.group(1).isEmpty() ? yamlSequence.size() : Integer.parseInt(m.group(1));
                    if (index < 0) index += yamlSequence.size();

                    if (m.end() == s.length()) {
                        specHandler.handleSequenceElement(yamlSequence, index);
                        return;
                    }

                    el = yamlSequence.get(index);
                    assert el != null;
                } else
                if ((m = SET_MEMBER_SPEC.matcher(s)).lookingAt()) {  // {<yaml-document>}

                    if (!(el instanceof Set)) throw new IllegalArgumentException("Element is not a set");
                    @SuppressWarnings("unchecked") @NotNull List<Object> yamlSequence = (List<Object>) el;
                    @SuppressWarnings("unchecked") Set<Object> yamlSet = (Set<Object>) el;

                    Object member = YamlPatch.loadYaml(new StringReader(m.group(1)));

                    specHandler.handleSetMember(yamlSet, member);
                    return;
                } else
                {
                    throw new IllegalArgumentException("Invalid spec \"" + s + "\"");
                }
            } catch (RuntimeException e) {
                throw ExceptionUtil.wrap(
                    "Applying spec \"" + spec + "\" at offset " + (spec.length() - s.length()) + " on \"" + el + "\"",
                    e
                );
            }
        }
    }

    public void
    transform(Reader in, OutputStream out) throws IOException {

        // Read the document from the reader.
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();
        Load load = new Load(settings);

        @NotNull Object yamlDocument = load.loadFromReader(in);

        for (Transformer<Object, Object> dm : YamlPatch.this.documentModifiers) {
            yamlDocument = dm.transform(yamlDocument);
        }

        // Write the document to the output stream.
        DumpSettings dumpSettings = (
            DumpSettings.builder()
            .setDefaultFlowStyle(flowStyle)
            .build()
        );
        Dump dump = new Dump(dumpSettings);
        try {
            dump.dump(yamlDocument, new YamlOutputStreamWriter(System.out, StandardCharsets.UTF_8) {
    
                @Override public void
                processIOException(@Nullable IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
        } catch (RuntimeException re) {
            Throwable cause = re.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw re;
        }
    }

    public ContentsTransformer
    contentsTransformer() {

        return new ContentsTransformer() {
            
            @Override public void
            transform(String path, InputStream is, OutputStream os) throws IOException {

//                for(;;) System.err.println(System.in.read());

                InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
                
                YamlPatch.this.transform(r, os);
            }
        };
    }

    public FileTransformer
    fileTransformer() {
        return new FileContentsTransformer(this.contentsTransformer(), this.keepOriginals);
    }

    public static Object
    loadYaml(Reader r) {
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();
        Load load = new Load(settings);
    
        return load.loadFromReader(r);
    }
}
