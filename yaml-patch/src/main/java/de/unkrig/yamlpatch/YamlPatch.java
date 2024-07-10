
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

package de.unkrig.yamlpatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.DumpSettingsBuilder;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.AnchorNode;
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

    private final DumpSettingsBuilder           dumpSettingsBuilder = DumpSettings.builder();
    private final List<Transformer<Node, Node>> documentModifiers = new ArrayList<>();

    /**
     * @return The modifiable {@link DumpSettingsBuilder} that will take effect for the next {@link #transform(Reader,
     *         OutputStream)} operation
     */
    public DumpSettingsBuilder
    getDumpSettingsBuilder() { return this.dumpSettingsBuilder; }

    /**
     * @see #set(Node, String, Node, SetMode, boolean, boolean)
     */
    public void
    addSet(String spec, Node value, SetMode mode, boolean commentOutOriginalEntry, boolean prependMap) throws IOException {
        this.documentModifiers.add(root -> this.set(root, spec, value, mode, commentOutOriginalEntry, prependMap));
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
     * @see #add(Node, String, AddMode, boolean)
     */
    public void
    addAdd(String spec, AddMode mode, boolean prependSet) throws IOException {
        this.documentModifiers.add(root -> this.add(root, spec, mode, prependSet));
    }
    public static enum AddMode { ANY, NON_EXISTING }

    
    /**
     * @see #sort(Node, String)
     */
    public void
    addSort(String spec, boolean reverse) throws IOException {
    	this.documentModifiers.add(root -> this.sort(root, spec, reverse));
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

    /**
     * Writes the given <var>node</var> to the given {@link OutputStream}, as configured by the {@link
     * #getDumpSettingsBuilder()}
     * 
     * @see DumpSettingsBuilder
     */
    public void
    dump(Node node, OutputStream out) {

        Dump dump = new Dump(this.dumpSettingsBuilder.build());

        dump.dumpNode(node, new YamlOutputStreamWriter(out, StandardCharsets.UTF_8) {
   
            @Override public void
            processIOException(@Nullable IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
    }

    public ContentsTransformer
    contentsTransformer() {

        return new ContentsTransformer() {
            
            @Override public void
            transform(String path, InputStream is, OutputStream os) throws IOException {
                InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8);
                
                YamlPatch.this.transform(r, os);
            }
        };
    }

    public FileTransformer
    fileTransformer(boolean keepOriginals) {
        return new FileContentsTransformer(this.contentsTransformer(), keepOriginals);
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
     * Adds or changes a map entry or a sequence element somewhere in a YAML document.
     *
     * @param spec                    Specifies the map entry or sequence element within the document
     * @param commentOutOriginalEntry Iff this changes an existing map entry, or an existing sequence element, add
     *                                an end comment to the map resp. sequence that displays the original map
     *                                entry resp. sequence element
     * @param prependMap              Add the new map entry at the beginning (instead of to the end)
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified map entry does not exist
     * @throws SpecMatchException     <var>mode</var> is {@code NON_EXISTING}, and the specified map entry does exist
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified sequence index is out of
     *                                range
     * @throws SpecMatchException     <var>mode</var> is {@code NON_EXISTING}, and the specified sequence index does
     *                                not equal the sequence size
     * @throws SpecMatchException     See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException    See {@link #processSpec(Node, String, SpecHandler)}
     */
    private static Node
    set(Node root, String spec, Node value, SetMode mode, boolean commentOutOriginalEntry, boolean prependMap) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
                Node prev = YamlPatch.put(map, key, value, commentOutOriginalEntry, prependMap);
                switch (mode) {
                case ANY:
                    break;
                case EXISTING:
                    if (prev == null) throw new SpecMatchException("Entry key \"" + YamlPatch.toString(key) + "\" does not exist");
                    break;
                case NON_EXISTING:
                    if (prev != null) throw new SpecMatchException("Entry key \"" + YamlPatch.toString(key) + "\" already exists");
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
                        
                        addNodeAsComments(new SequenceNode(Tag.SEQ, List.of(prev), sequence.getFlowStyle()), ecs);
                    }
                }
            }
        });
        
        return root;
    }

    /**
     * Removes one map entry or sequence element somewhere in a YAML document.
     * 
     * @param spec                    Specifies the map entry or sequence element within the document
     * @param mode                    (Irrelevant if an sequence element is specified)
     * @param commentOutOriginalEntry Iff a map entry or sequence element was removed, add an end comment to the map
     *                                resp. sequence that displays the removed map entry resp. sequence element
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified map key does not exist
     * @throws SpecMatchException     The specified sequence index is out of range (-sequenceSize ... sequenceSize-1)
     * @throws SpecMatchException     <var>mode</var> is {@code EXISTING}, and the specified set member does not exist
     * @throws SpecMatchException     See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException    See {@link #processSpec(Node, String, SpecHandler)}
     */
    private static Node
    remove(Node root, String spec, RemoveMode mode, boolean commentOutOriginalEntry) {
        
        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
                if (remove(map, key, commentOutOriginalEntry) == null && mode == RemoveMode.EXISTING) throw new SpecMatchException("Key \"" + YamlPatch.toString(key) + "\" does not exist");
            }
            
            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                if (index < 0 || index >= sequence.getValue().size()) throw new SpecMatchException("Sequence index " + index + " is out of range");
                remove(sequence, index, commentOutOriginalEntry);
            }
        });

        return root;
    }

    /**
     * Inserts an element into, or adds at the the end of a sequence somewhere in a YAML document.
     *
     * @param spec                 Specifies the sequence element within the document
     * @throws SpecMatchException  The <var>spec</var> specified an map (and not an sequence)
     * @throws SpecMatchException  The specified sequence index is out of range (-sequenceSize ... sequenceSize)
     * @throws SpecMatchException  See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException See {@link #processSpec(Node, String, SpecHandler)}
     */
    private static Node
    insert(Node root, String spec, Node sequenceElement) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, @Nullable Node key) {
                throw new SpecMatchException("Cannot insert into map; use SET or ADD instead");
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                if (index < 0 || index > sequence.getValue().size()) throw new SpecMatchException("Sequence index " + index + " is out of range");
                sequence.getValue().add(index, sequenceElement);
            }
        });
        return root;
    }

    /**
     * Adds an entry with no value to a map somewhere in a YAML document. (Typically used for sets, which are
     * effectively maps with only keys and no values.)
     *
     * @param spec                 Specifies the set within the document and the value to add
     * @param prependSet           Add the member at the beginning of the set (instead of to the end)
     * @throws SpecMatchException  The <var>spec</var> specified a sequence (and not a set or a map)
     * @throws SpecMatchException  See {@link #processSpec(Node, String, SpecHandler)}
     * @throws SpecSyntaxException See {@link #processSpec(Node, String, SpecHandler)}
     */
    private static Node
    add(Node root, String spec, AddMode mode, boolean prependSet) {

        YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
                Node prev = YamlPatch.put(
                    map,                                             // map
                    key,                                             // key
                    new ScalarNode(Tag.NULL, "", ScalarStyle.PLAIN), // value
                    false,                                           // commentOutOriginalEntry
                    prependSet                                       // prependMap
                );
                switch (mode) {
                case ANY:
                    break;
                case NON_EXISTING:
                    if (prev != null) throw new SpecMatchException("Key \"" + YamlPatch.toString(key) + "\" already exists");
                    break;
                }
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
                throw new SpecMatchException("Cannot add to sequence; use INSERT instead");
            }
        });
        return root;
    }

    /**
     * Sorts the elements of a sequences, or the value tuples of a mapping by key
     *
     * @param spec Specifies the map or sequence within the document
     */
    private static Node
    sort(Node root, String spec, boolean reverse) {

        YamlPatch.processSpec(root, spec, new SpecHandler2() {

            @Override public void
            handleScalar(ScalarNode scalar) {
            	throw new SpecSyntaxException("Cannot sort scalar \"" + YamlPatch.toString(scalar) + "\", only sequences and maps");
			}

			@Override public void
			handleSequence(SequenceNode sequence) {
				YamlPatch.sort(sequence, reverse);
			}

			@Override public void
			handleMap(MappingNode map) {
				YamlPatch.sort(map, reverse);
			}

			@Override public void
			handleAnchor(AnchorNode anchor) {
				throw new SpecSyntaxException("Cannot sort anchor \"" + YamlPatch.toString(anchor) + "\", only sequences and maps");
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
    @Nullable private static Node
    put(MappingNode map, Node key, Node value, boolean commentOutOriginalEntry, boolean prependMap) {
        List<NodeTuple> entries = map.getValue();
        for (int index = 0; index < entries.size(); index++) {
            NodeTuple nt = entries.get(index);
            if (equals(key, nt.getKeyNode())) {
                Node result = nt.getValueNode();
                if (commentOutOriginalEntry) {

                    List<CommentLine> bcs = key.getBlockComments();
                    if (bcs == null) key.setBlockComments((bcs = new ArrayList<CommentLine>()));

                    addNodeAsComments(new MappingNode(Tag.MAP, List.of(nt), map.getFlowStyle()), bcs);
                }
                entries.set(index, new NodeTuple(key, value));
                return result;
            }
        }
        if (prependMap) {
            entries.add(0, new NodeTuple(key, value));
        } else {
            entries.add(new NodeTuple(key, value));
        }
        return null;
    }

    /**
     * Sorts the value tuples of a {@link MappingNode} by key.
     */
    private static void
    sort(MappingNode mappingNode, boolean reverse) {
    	mappingNode.getValue().sort((a, b) -> reverse ? compare(b.getKeyNode(), a.getKeyNode()) : compare(a.getKeyNode(), b.getKeyNode()));
    }

    /**
     * Sorts the elements of a {@link SequenceNode}.
     */
    private static void
    sort(SequenceNode sequenceNode, boolean reverse) {
    	sequenceNode.getValue().sort((a, b) -> reverse ? compare(b, a) : compare(a, b));
    }

	private static int
	compare(Node a, Node b) {

		NodeType nodeTypeA = a.getNodeType();
		NodeType nodeTypeB = b.getNodeType();
		if (nodeTypeA != nodeTypeB) return nodeTypeA.ordinal() - nodeTypeB.ordinal();
		
		switch (nodeTypeA) {
		case SCALAR:   return compare((ScalarNode)   a, (ScalarNode)   b); 
		case SEQUENCE: return compare((SequenceNode) a, (SequenceNode) b); 
		case MAPPING:  return compare((MappingNode)  a, (MappingNode)  b); 
		case ANCHOR:   return compare((AnchorNode)   a, (AnchorNode)   b); 
		default:
			throw new AssertionError(nodeTypeA);
		}
	}

	private static int
	compare(ScalarNode scalarA, ScalarNode scalarB) {
		return scalarA.getValue().compareTo(scalarB.getValue());
	}

	private static int
	compare(SequenceNode sequenceA, SequenceNode sequenceB) {
		return compare(sequenceA.getValue(), sequenceB.getValue(), (elementA, elementB) -> compare(elementA, elementB));
	}

	private static int
	compare(MappingNode mapA, MappingNode mapB) {
		return compare(mapA.getValue(), mapB.getValue(), (tupleA, tupleB) -> {
			int result = compare(tupleA.getKeyNode(), tupleB.getKeyNode());
			if (result != 0) return result;
			return compare(tupleA.getValueNode(), tupleB.getValueNode());
		});
	}

	private static int
	compare(AnchorNode anchorA, AnchorNode anchorB) {
		return compare(anchorA.getRealNode(), anchorB.getRealNode());
	}

	/**
	 * Compares two {@link List}s element by element. Iff all elements are equal, but the lengths are not, then the
	 * shorter list is considered "less than" the longer list.
	 */
	private static <T> int
	compare(List<T> listA, List<T> listB, Comparator<T> comparator) {
		for (Iterator<T> iteratorA = listA.iterator(), iteratorB = listB.iterator();;) {
			if (!iteratorA.hasNext()) return iteratorB.hasNext() ? -1 : 0;
			if (!iteratorB.hasNext()) return 1;
			
			int result = comparator.compare(iteratorA.next(), iteratorB.next());
			if (result != 0) return result;
		}
	}

    /**
     * Adds a series of block comments to the <var>result</var> that resemble the original <var>node</var>.
     */
    private static void
    addNodeAsComments(Node node, List<CommentLine> result) {
        for (String line : dumpNoComments(node).split("\\r?\\n")) {
            result.add(new CommentLine(Optional.empty(), Optional.empty(), " " + line, CommentType.BLOCK));
        }
    }

    /**
     * Removes an entry from the <var>map</var> if it exists.
     *
     * @param commentOutOriginalEntry Iff a map entry was removed, add an end comment to the map that displays the
     *                                removed map entry
     * @return                        The previous entry value, nor {@code null} iff no entry was removed
     */
    @Nullable private static Node
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
                    addNodeAsComments(tmp, ecs);
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
    @Nullable private static Node
    remove(SequenceNode sequence, int index, boolean commentOutOriginalEntry) {
        List<Node> elements = sequence.getValue();
        Node result = elements.remove(index);
    
        if (commentOutOriginalEntry) {

            List<CommentLine> bcs = sequence.getEndComments();
            if (bcs == null) sequence.setEndComments((bcs = new ArrayList<CommentLine>()));

            addNodeAsComments(new SequenceNode(Tag.SEQ, List.of(result), sequence.getFlowStyle()), bcs);
        }
        return result;
    }

    public
    interface SpecHandler {

        /**
         * Designated node is a map (maybe with a "{@link Tag#SET set}" {@link Node#getTag() tag}).
         */
        void handleMapEntry(MappingNode map, Node key);

        /**
         * Designated node is a sequence.
         */
        void handleSequenceElement(SequenceNode sequence, int index);
    }
    
    public
    interface SpecHandler2 {
    	
    	/**
    	 * Designated node is a scalar.
    	 */
    	void handleScalar(ScalarNode scalar);
    	
    	/**
    	 * Designated node is a sequence.
    	 */
    	void handleSequence(SequenceNode sequence);
    	
    	/**
    	 * Designated node is a map (maybe with a "{@link Tag#SET set}" {@link Node#getTag() tag}).
    	 */
    	void handleMap(MappingNode map);
    	
    	/**
    	 * Designated node is an anchor.
    	 */
    	void handleAnchor(AnchorNode anchor);
    }
    
    public
    interface SpecHandler3 {
    	void handleNode(Node node);
    }

    private static final Pattern MAP_ENTRY_SPEC1       = Pattern.compile("\\.([A-Za-z0-9_\\-]+)");
    private static final Pattern MAP_ENTRY_SPEC2       = Pattern.compile("\\.\\((.*)");
    private static final Pattern SEQUENCE_ELEMENT_SPEC = Pattern.compile("\\[(-?\\d*)]");

    /**
     * Parses the <var>spec</var>, locates the relevant node in the <var>root</var> document, and invokes one of the
     * methods of the <var>specHandler</var>.
     * 
     * @throws SpecMatchException A map entry spec was applied to a non-map element
     * @throws SpecMatchException A map entry spec designates a non-existing key
     * @throws SpecMatchException A sequence element spec was applied to a non-sequence element
     * @throws SpecMatchException A sequence index was out-of-range (except for the last segment of the <var>spec</var>)
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

                    Node key;
                    if (m.pattern() == MAP_ENTRY_SPEC1) {
                        key = new ScalarNode(Tag.STR, m.group(1), ScalarStyle.PLAIN);
                        s.delete(0, m.end());
                    } else
                    if (m.pattern() == MAP_ENTRY_SPEC2) {
                        s.delete(0, 2);
                        key = YamlPatch.loadFirst(s);
                        if (s.length() == 0 || s.charAt(0) != ')') throw new SpecSyntaxException("Closing parenthesis missing after map key \"" + toString(key) + "\"");
                        s.delete(0, 1);
                    } else
                    {
                        throw new AssertionError(m.pattern());
                    }
                    
                    if (el.getNodeType() == NodeType.MAPPING) {
                        MappingNode yamlMap = (MappingNode) el;
                        
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
                    } else
                    if (el.getNodeType() == NodeType.SEQUENCE) {
                        SequenceNode yamlSequence = (SequenceNode) el;

                        List<Node> elements = yamlSequence.getValue();
                        for (int index = 0; index < elements.size(); index++) {
                            Node sequenceElement = elements.get(index);
                            if (equals(sequenceElement, key)) {
                                if (s.length() == 0) {
                                    specHandler.handleSequenceElement(yamlSequence, index);
                                    return;
                                }
                                
                                el = sequenceElement;
                                continue SPEC;
                            }
                        }
                        throw new SpecMatchException("Sequence does not contain an element \"" + toString(key) + "\"");
                    } else
                    {
                        throw new SpecMatchException("Element is not a map nor a sequence");
                    }
                    throw new SpecMatchException("Map does not contain key \"" + toString(key) + "\"");
                } else
                if ((m = SEQUENCE_ELEMENT_SPEC.matcher(s)).lookingAt()) {  // [<integer>], []

                    if (el.getNodeType() != NodeType.SEQUENCE) throw new SpecMatchException("Element is not a sequence");
                    SequenceNode yamlSequence = (SequenceNode) el;
                    List<Node> value = yamlSequence.getValue();

                    int index = m.group(1).isEmpty() ? value.size() : Integer.parseInt(m.group(1));
                    if (index < 0) index += value.size();

                    if (m.end() == s.length()) {
                        specHandler.handleSequenceElement(yamlSequence, index);
                        return;
                    }

                    if (index < 0 || index >= value.size()) throw new SpecMatchException("Index " + index + " is out of range; sequence \"" + YamlPatch.toString(yamlSequence) + "\" has " + value.size() + " elements");
                    el = value.get(index);
                    assert el != null;
                    s.delete(0, m.end());
                } else
                {
                    throw new SpecSyntaxException("Invalid spec \"" + s + "\"");
                }
            } catch (RuntimeException e) {
                throw ExceptionUtil.wrap(
                    "Applying spec \"" + spec + "\" at offset " + (spec.length() - s.length()) + " on \"" + toString(el) + "\"",
                    e
                );
            }
        }
    }

    /**
     * Parses the <var>spec</var>, locates the relevant node in the <var>root</var> document, and invokes one of the
     * methods of the <var>specHandler2</var>.
     * 
     * @throws SpecMatchException A map entry spec was applied to a non-map element
     * @throws SpecMatchException A map entry spec designates a non-existing key
     * @throws SpecMatchException A sequence element spec was applied to a non-sequence element
     * @throws SpecMatchException A sequence index was out-of-range
     * @throws SpecMatchException A set member spec was applied to a non-set element
     * @throws SpecSyntaxException
     */
    private static void
    processSpec(Node root, String spec, SpecHandler2 specHandler2) {
    	processSpec(root, spec, new SpecHandler3() {

			@Override public void
			handleNode(Node node) {
				switch (node.getNodeType()) {
				
				case SCALAR:
					specHandler2.handleScalar((ScalarNode) node);
					break;
					
				case SEQUENCE:
					specHandler2.handleSequence((SequenceNode) node);
					break;
					
				case MAPPING:
					specHandler2.handleMap((MappingNode) node);
					break;
					
				case ANCHOR:
					specHandler2.handleAnchor((AnchorNode) node);
					break;
				}
			}
		});
    }

    private static void
	processSpec(Node root, String spec, SpecHandler3 specHandler3) {

    	if ("".equals(spec)) {
    		specHandler3.handleNode(root);
    		return;
    	}

    	YamlPatch.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
            	for (NodeTuple mapElement : map.getValue()) {
            		if (YamlPatch.equals(mapElement.getKeyNode(), key)) {
            			specHandler3.handleNode(mapElement.getValueNode());
            			return;
            		}
            	}
            	throw new SpecMatchException("Map \"" + YamlPatch.toString(map) + "\" lacks key \"" + YamlPatch.toString(key) + "\"");
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
            	List<Node> sequenceElements = sequence.getValue();
            	if (index < 0 || index >= sequenceElements.size()) throw new SpecMatchException("Index " + index + " out of range");
            	specHandler3.handleNode(sequenceElements.get(index));
            }
        });
    }

    /**
     * Loads the first node of a YAML document and removes the parsed characters from the <var>sb</sb>.
     */
    private static Node
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

    private static boolean
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
     * @return The YAML document in "FLOW" (single-line) style; useful e.g. for generating error messages
     */
    public static String
    toString(Node node) {
        String result = toString(node, DumpSettings.builder().setDefaultFlowStyle(FlowStyle.FLOW).build());
        result = result.trim();
        if (result.length() > 30) result = result.substring(0, 20) + "...";
        return result;
    }

    /**
     * @return The YAML document
     */
    private static String
    toString(Node node, DumpSettings dumpSettings) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Dump(dumpSettings).dumpNode(node, new YamlOutputStreamWriter(baos, StandardCharsets.UTF_8) {
   
            @Override public void
            processIOException(@Nullable IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * @return The given <var>node</var>, formatted in its original flow style, including comments
     */
    public static String
    dump(Node node) {
        return toString(node, DumpSettings.builder().setDumpComments(true).build());
    }
    
    /**
     * @return The given <var>node</var>, formatted in its original flow style, but without any comments
     */
    public static String
    dumpNoComments(Node node) {
        return toString(node, DumpSettings.builder().build());
    }
}
