
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.CollectionNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import de.unkrig.commons.file.contentstransformation.ContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileContentsTransformer;
import de.unkrig.commons.file.filetransformation.FileTransformer;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.ExceptionUtil;
import de.unkrig.commons.lang.protocol.Transformer;
import de.unkrig.commons.nullanalysis.Nullable;

public
class YamlPatch {

    static {
        AssertionUtil.enableAssertionsForThisClass();
    }

    private boolean                             keepOriginals;
    private final List<Transformer<Node, Node>> documentModifiers = new ArrayList<>();

    public void
    setKeepOriginals(boolean value) { this.keepOriginals = value; }

    /**
     * @see #set(Node, String, Node, SetMode, boolean)
     */
    public void
    addSet(String spec, Node value, SetMode mode, boolean commentOutOriginalEntry) throws IOException {
        this.documentModifiers.add(root -> this.set(root, spec, value, mode, commentOutOriginalEntry));
    }
    public static enum SetMode { ANY, EXISTING, NON_EXISTING }

    /**
     * @see #remove(Node, String, RemoveMode, boolean)
     */
    public void
    addRemove(String spec, RemoveMode mode, boolean commentOutOriginalEntry) throws IOException {
        this.documentModifiers.add(root -> this.remove(root, spec, mode, commentOutOriginalEntry));
    }
    public static enum RemoveMode { ANY, EXISTING }

    /**
     * @see #insert(Node, String, Node)
     */
    public void
    addInsert(String spec, Node sequenceElement) throws IOException {
        this.documentModifiers.add(root -> this.insert(root, spec, sequenceElement));
    }
    
    /**
     * @see #add(Node, String)
     */
    public void
    addAdd(String spec) throws IOException {
        this.documentModifiers.add(root -> this.add(root, spec));
    }

    /**
     * Adds or changes a map entry or a sequence element somewhere in a YAML document.
     *
     * @param spec                    Specifies the map entry or sequence element within the document
     * @param commentOutOriginalEntry Iff this changes an existing map entry, or an existing sequence element, add
     *                                an end comment to the map resp. sequence that displays the original map
     *                                entry resp. sequence element
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified map entry does not exist
     * @throws SpecMatchException     <var>mode</var> is {@code NON_EXISTING}, and the specified map entry does exist
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified sequence index is out of
     *                                range
     * @throws SpecMatchException     <var>mode</var> is {@code NON_EXISTING}, and the specified sequence index does
     *                                not equal the sequence size
     * @throws SpecMatchException     The designated node is a set (use {@link #add(Node, String)} instead)
     * @throws SpecMatchException     See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException    See {@link #processSpec(Node, String, SpecHandler)}
     */
    public Node
    set(Node root, String spec, Node value, SetMode mode, boolean commentOutOriginalEntry) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
                Node prev = put(map, key, value, commentOutOriginalEntry);
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (prev == null) throw new SpecMatchException("Entry key \"" + dump(key, false) + "\" does not exist");
                    break;
                case NON_EXISTING:
                    if (prev != null) throw new SpecMatchException("Entry key \"" + dump(key, false) + "\" already exists");
                    break;
                }
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                List<Node> sequenceElements = sequence.getValue();
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (index < 0 || index >= sequenceElements.size()) throw new SpecMatchException("Index " + index + " out of range");
                    break;
                case NON_EXISTING:
                    if (index != sequenceElements.size()) throw new SpecMatchException("Index " + index + " not equal to sequence size");
                    break;
                }
                if (index == sequenceElements.size()) {
                    sequenceElements.add(value);
                } else {
                    Node prev = sequenceElements.set(index, value);
                    if (commentOutOriginalEntry) {
                        
                        List<CommentLine> ecs = sequence.getEndComments();
                        if (ecs == null) sequence.setEndComments((ecs = new ArrayList<>()));
                        
                        SequenceNode tmp = new SequenceNode(Tag.SEQ, List.of(prev), sequence.getFlowStyle());
                        for (String line : dump(tmp, false).split("\\r?\\n")) {
                            ecs.add(new CommentLine(Optional.empty(), Optional.empty(), line, CommentType.BLOCK));
                        }
                    }
                }
            }

            @Override public void
            handleSetMember(MappingNode set, @Nullable Node member) {
                throw new SpecMatchException("Cannot \"set\"; use \"add\" for yaml sets");
            }
        });
        
        return root;
    }

    /**
     * Removes one sequence element, map entry or set member somewhere in a YAML document.
     * 
     * @param spec                    Specifies the map entry, sequence element or set member within the document
     * @param mode                    (Irrelevant if an sequence element is specified)
     * @param commentOutOriginalEntry Iff a map entry, sequence element or set member was removed, add an end comment
     *                                to the map resp. sequence resp. set that displays the removed map entry resp.
     *                                sequence element resp. set member
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified map key does not exist
     * @throws SpecMatchException     The specified sequence index is out of range (-sequenceSize ... sequenceSize-1)
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified set member does not exist
     * @throws SpecMatchException     See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException    See {@link #processSpec(Node, String, SpecHandler)}
     */
    public Node
    remove(Node root, String spec, RemoveMode mode, boolean commentOutOriginalEntry) {
        
        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
                if (remove(map, key, commentOutOriginalEntry) == null && mode == RemoveMode.EXISTING) throw new SpecMatchException("Key \"" + dump(key, false) + "\" does not exist");
            }
            
            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                if (index < 0 || index >= sequence.getValue().size()) throw new SpecMatchException("Sequence index " + index + " is out of range");
                remove(sequence, index, commentOutOriginalEntry);
            }

            @Override public void
            handleSetMember(MappingNode set, Node member) {
                if (remove(set, member, commentOutOriginalEntry) == null && mode == RemoveMode.EXISTING) throw new SpecMatchException("Member \"" + dump(member, false) + "\" does not exist");
            }
        });

        return root;
    }

    /**
     * Inserts an element into, or adds at the the end of a sequence somewhere in a YAML document.
     *
     * @param spec                 Specifies the sequence element within the document
     * @throws SpecMatchException  The <var>spec</var> specified an map or a set (and not an sequence)
     * @throws SpecMatchException  The specified sequence index is out of range (-sequenceSize ... sequenceSize)
     * @throws SpecMatchException  See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException See {@link #processSpec(Node, String, SpecHandler)}
     */
    public Node
    insert(Node root, String spec, Node sequenceElement) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, @Nullable Node key) {
                throw new SpecMatchException("Cannot insert into map; use SET instead");
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                if (index < 0 || index > sequence.getValue().size()) throw new SpecMatchException("Sequence index " + index + " is out of range");
                sequence.getValue().add(index, sequenceElement);
            }

            @Override public void
            handleSetMember(MappingNode set, @Nullable Node member) {
                throw new SpecMatchException("Cannot insert into set; use ADD instead");
            }
        });
        return root;
    }

    /**
     * Adds a member to a set somewhere in a YAML document.
     *
     * @param spec                 Specifies the set within the document and the value to add
     * @throws SpecMatchException  The <var>spec</var> specified a map or a sequence (and not a set)
     * @throws SpecMatchException  See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException See {@link #processSpec(Node, String, SpecHandler)}
     */
    public Node
    add(Node root, String spec) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, @Nullable Node key) {
                throw new SpecMatchException("Cannot add to map; use SET instead");
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                throw new SpecMatchException("Cannot add to sequence; use INSERT instead");
            }

            @Override public void
            handleSetMember(MappingNode set, Node member) {
                put(set, member, new ScalarNode(null, "null", ScalarStyle.PLAIN), false);
            }
        });
        return root;
    }

    /**
     * Adds a new entry with the given key and value, or changes the value of the existing entry.
     * 
     * @param commentOutOriginalEntry Iff this changes an existing map entry, add an end comment to the map that
     *                                displays the original map entry
     * @return                        The previous value, or {@code null} iff entry with the given key does not exist
     */
    @Nullable static Node
    put(MappingNode map, Node key, Node value, boolean commentOutOriginalEntry) {
        List<NodeTuple> entries = map.getValue();
        for (int index = 0; index < entries.size(); index++) {
            NodeTuple nt = entries.get(index);
            if (equals(key, nt.getKeyNode())) {
                Node result = nt.getValueNode();
                if (commentOutOriginalEntry) {

                    List<CommentLine> bcs = key.getBlockComments();
                    if (bcs == null) key.setBlockComments((bcs = new ArrayList<CommentLine>()));

                    MappingNode tmp = new MappingNode(Tag.MAP, List.of(nt), map.getFlowStyle());
                    for (String line : dump(tmp, false).split("\\r?\\n")) {
                        bcs.add(new CommentLine(Optional.empty(), Optional.empty(), " " + line, CommentType.BLOCK));
                    }
                }
                entries.set(index, new NodeTuple(key, value));
                return result;
            }
        }
        entries.add(new NodeTuple(key, value));
        return null;
    }

    /**
     * Removes an entry from the <var>map</var> if it exists.
     *
     * @param commentOutOriginalEntry Iff a map entry was removed, add an end comment to the map that displays the
     *                                removed map entry
     * @return                        The previous entry value, nor {@code null} iff no entry was removed
     */
    @Nullable private Node
    remove(MappingNode map, Node key, boolean commentOutOriginalEntry) {
        List<NodeTuple> mapTuples = map.getValue();

        for (int i = 0; i < mapTuples.size(); i++) {
            if (equals(mapTuples.get(i).getKeyNode(), key)) {
                NodeTuple nt = mapTuples.get(i);
                Node result = nt.getValueNode();
                mapTuples.remove(i);
                if (commentOutOriginalEntry) {
                    List<CommentLine> ecs = map.getEndComments();
                    if (ecs == null)  map.setEndComments((ecs = new ArrayList<CommentLine>()));
                    MappingNode tmp = new MappingNode(Tag.MAP, List.of(nt), map.getFlowStyle());
                    for (String line : dump(tmp, false).split("\\r?\\n")) {
                        ecs.add(new CommentLine(Optional.empty(), Optional.empty(), " " + line, CommentType.BLOCK));
                    }
                }
                return result;
            }
        }
        return null;
    }

    /**
     * Removes an element from a sequence.
     * 
     * @param commentOutOriginalEntry Add an end comment to the sequence that displays the removed sequence element
     * @return                        The removed sequence member
     */
    @Nullable private Node
    remove(SequenceNode sequence, int index, boolean commentOutOriginalEntry) {
        List<Node> elements = sequence.getValue();
        Node result = elements.remove(index);
    
        if (commentOutOriginalEntry) {

            List<CommentLine> bcs = sequence.getEndComments();
            if (bcs == null) sequence.setEndComments((bcs = new ArrayList<CommentLine>()));

            SequenceNode tmp = new SequenceNode(Tag.SEQ, List.of(result), sequence.getFlowStyle());
            for (String line : dump(tmp, false).split("\\r?\\n")) {
                bcs.add(new CommentLine(Optional.empty(), Optional.empty(), " " + line, CommentType.BLOCK));
            }
        }
        return result;
    }

    public
    interface SpecHandler {

        /**
         * Designated node is a map.
         */
        void handleMapEntry(MappingNode map, Node key);

        /**
         * Designated node is a sequence.
         */
        void handleSequenceElement(SequenceNode sequence, int index);
        
        /**
         * Designated node is a set.
         */
        void handleSetMember(MappingNode set, Node member);
    }

    private static final Pattern MAP_ENTRY_SPEC1       = Pattern.compile("\\.([A-Za-z0-9_]+)");
    private static final Pattern MAP_ENTRY_SPEC2       = Pattern.compile("\\.\\((.*)");
    private static final Pattern SEQUENCE_ELEMENT_SPEC = Pattern.compile("\\[(-?\\d*)]");
    private static final Pattern SET_MEMBER_SPEC       = Pattern.compile("\\(");

    /**
     * Parses the <var>spec</var>, locates the relevant node in the <var>root</var> document, and invokes one of the
     * methods of the <var>specHandler</var>.
     * 
     * @throws SpecMatchException A map entry spec was applied to a non-map element
     * @throws SpecMatchException A map entry spec designates a non-existing key
     * @throws SpecMatchException A sequence element spec was applied to a non-sequence element
     * @throws SpecMatchException A set member spec was applied to a non-set element
     * @throws SpecSyntaxException
     */
    private static void
    processSpec(Node root, String spec, SpecHandler specHandler) {

        Node el = root;
        Matcher m;
        SPEC: for (StringBuilder s = new StringBuilder(spec);;) {
            try {

                if (
                    (m = MAP_ENTRY_SPEC1.matcher(s)).lookingAt()     // .<identifier>
                    || (m = MAP_ENTRY_SPEC2.matcher(s)).lookingAt()  // .(<yaml-document>)
                ) {
                    
                    if (el.getNodeType() != NodeType.MAPPING) throw new SpecMatchException("Element is not a map");
                    MappingNode yamlMap = (MappingNode) el;

                    Node key;
                    if (m.pattern() == MAP_ENTRY_SPEC1) {
                        key = new ScalarNode(Tag.STR, m.group(1), ScalarStyle.PLAIN);
                        s.delete(0, m.end());
                    } else
                    if (m.pattern() == MAP_ENTRY_SPEC2) {
                        s.delete(0, 2);
                        key = YamlPatch.loadFirst(s);
                        if (s.length() == 0 || s.charAt(0) != ')') throw new SpecSyntaxException("Closing parenthesis missing after map key \"" + dump(key, false) + "\"");
                        s.delete(0, 1);
                    } else
                    {
                        throw new AssertionError(m.pattern());
                    }

                    if (s.length() == 0) {
                        specHandler.handleMapEntry(yamlMap, key);
                        return;
                    }

                    for (NodeTuple nt : yamlMap.getValue()) {
                        if (equals(nt.getKeyNode(), key)) {
                            el = nt.getValueNode();
                            continue SPEC;
                        }
                    }
                    throw new SpecMatchException("Map does not contain key \"" + dump(key, false) + "\"");
                } else
                if ((m = SEQUENCE_ELEMENT_SPEC.matcher(s)).lookingAt()) {  // [<integer>]

                    if (el.getNodeType() != NodeType.SEQUENCE) throw new SpecMatchException("Element is not a sequence");
                    SequenceNode yamlSequence = (SequenceNode) el;
                    List<Node> value = yamlSequence.getValue();

                    int index = m.group(1).isEmpty() ? value.size() : Integer.parseInt(m.group(1));
                    if (index < 0) index += value.size();

                    if (m.end() == s.length()) {
                        specHandler.handleSequenceElement(yamlSequence, index);
                        return;
                    }

                    el = value.get(index);
                    assert el != null;
                    s.delete(0, m.end());
                } else
                if ((m = SET_MEMBER_SPEC.matcher(s)).lookingAt()) {  // (<yaml-document>)

                    s.delete(0, 1);
                    Node member = YamlPatch.loadFirst(s);
                    if (s.length() == 0 || s.charAt(0) != ')') throw new SpecSyntaxException("Closing parenthesis missing after member \"" + dump(member, false) + "\"");
                    if (s.length() > 1) throw new SpecSyntaxException("Member spec must be terminal");

                    if (el.getNodeType() != NodeType.MAPPING) throw new SpecMatchException("Element is not a set");
                    MappingNode yamlSet = (MappingNode) el;

                    specHandler.handleSetMember(yamlSet, member);
                    return;
                } else
                {
                    throw new SpecSyntaxException("Invalid spec \"" + s + "\"");
                }
            } catch (RuntimeException e) {
                throw ExceptionUtil.wrap(
                    "Applying spec \"" + spec + "\" at offset " + (spec.length() - s.length()) + " on \"" + dump(el, false) + "\"",
                    e
                );
            }
        }
    }

    public void
    transform(Reader in, OutputStream out) throws IOException {

        // Read the document from the reader.
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).setParseComments(true).build();

        Node yamlDocument = new Compose(settings).composeReader(in).get();

        for (Transformer<Node, Node> dm : YamlPatch.this.documentModifiers) {
            yamlDocument = dm.transform(yamlDocument);
        }

        // Write the document to the output stream.
        dump(yamlDocument, out);
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

    /**
     * Loads a YAML document that defines exactly one node. 
     */
    public static Node
    loadYaml(Reader r) {
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();

        ParserImpl      parser   = new ParserImpl(settings, new StreamReader(settings, r));
        Composer        composer = new Composer(settings, parser);

        return composer.getSingleNode().get();
    }

    /**
     * Loads the first node of a YAML document and removes the parsed characters from the <var>sb</sb>.
     */
    public static Node
    loadFirst(StringBuilder sb) {
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();

        ParserImpl      parser   = new ParserImpl(settings, new StreamReader(settings, sb.toString()));
        Composer        composer = new Composer(settings, parser);

        // Drop the STREAM-START event.
        parser.next();

        Node node = composer.next();

        sb.delete(0, node.getEndMark().get().getIndex());

        return node;
    }

    public static boolean
    equals(Node a, Node b) {

        if (a == b) return true;

        if (a instanceof CollectionNode && b instanceof CollectionNode) {
            List<?> aValue = ((CollectionNode<?>) a).getValue();
            List<?> bValue = ((CollectionNode<?>) b).getValue();
            int size = aValue.size();
            if (size != bValue.size()) return false;
            for (int i = 0; i < size; i++) {
                Object av = aValue.get(i);
                Object bv = bValue.get(i);
                if (av instanceof NodeTuple && bv instanceof NodeTuple) {
                    Node aElementKey   = ((NodeTuple) av).getKeyNode();
                    Node aElementValue = ((NodeTuple) av).getValueNode();
                    Node bElementKey   = ((NodeTuple) bv).getKeyNode();
                    Node bElementValue = ((NodeTuple) bv).getValueNode();
                    
                    if (!(
                        equals(aElementKey, bElementKey)
                        && equals(aElementValue, bElementValue)
                    )) return false;
                } else
                if (av instanceof Node && bv instanceof Node) {
                    if (!equals((Node) av, (Node) bv)) return false;
                } else
                {
                    return false;
                }
            }
            return true;
        } else
        if (a instanceof ScalarNode && b instanceof ScalarNode) {
            return ((ScalarNode) a).getValue().equals(((ScalarNode) b).getValue());
        } else
        {
            return false;
        }
    }

    /**
     * @return The given <var>node</var>, formatted in its original flow style, including comments
     */
    public static String
    dump(Node node) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dump(node, baos);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * @return The given <var>node</var>, formatted in its original flow style, optionally including comments
     */
    public static String
    dump(Node node, boolean dumpComments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dump(node, baos, dumpComments);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Writes the given <var>node</var> to the given {@link OutputStream}, in its original flow style, including
     * comments.
     */
    public static void
    dump(Node node, OutputStream out) {
        dump(node, out, true);
    }

    /**
     * Writes the given <var>node</var> to the given {@link OutputStream}, in its original flow style, optionally
     * including comments.
     */
    public static void
    dump(Node node, OutputStream out, boolean dumpComments) {

        DumpSettings dumpSettings = (
            DumpSettings.builder()
            .setDumpComments(dumpComments)
            .build()
        );
        Dump dump = new Dump(dumpSettings);

        dump.dumpNode(node, new YamlOutputStreamWriter(out, StandardCharsets.UTF_8) {
   
            @Override public void
            processIOException(@Nullable IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
    }
}
