
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.Node;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformer.Mode;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOption.Cardinality;
import de.unkrig.zz.yamlpatch.YamlPatch.AddMode;
import de.unkrig.zz.yamlpatch.YamlPatch.RemoveMode;
import de.unkrig.zz.yamlpatch.YamlPatch.SetMode;

public
class Main {

    private boolean         keepOriginals;
    private final YamlPatch yamlPatch = new YamlPatch();
    { this.yamlPatch.getDumpSettingsBuilder().setDumpComments(true); }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * For in-place transformations, keep copies of the originals
     * 
     * @main.commandLineOptionGroup File-Processing
     */
    @CommandLineOption public void
    keep() { this.keepOriginals = true; }

    // =============================== DumpSettingsBuider settings. ===============================

    /**
     * CR, LF, CRLF or any other value
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setBestLineBreak(String value) { this.yamlPatch.getDumpSettingsBuilder().setBestLineBreak((
        "CR".equals(value) ? "\r" :
        "LF".equals(value) ? "\n" :
        "CRLF".equals(value) ? "\r\n" :
        value
    )); }
    /**
     * Formatting style for generated documents
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDefaultFlowStyle(FlowStyle value) { this.yamlPatch.getDumpSettingsBuilder().setDefaultFlowStyle(value); }
    /**
     * Remove comments while transforming
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setStripComments() { this.yamlPatch.getDumpSettingsBuilder().setDumpComments(false); }
    /**
     * Number of spaces for the indent in the block flow style (default 2)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndent(int value) { this.yamlPatch.getDumpSettingsBuilder().setIndent(value); }
    /**
     * Add the indent for sequences to the general indent
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndentWithIndicator() { this.yamlPatch.getDumpSettingsBuilder().setIndentWithIndicator(true); }
    /**
     * Add the specified indent for sequence indicator in the block flow (default 0)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndicatorIndent(int value) { this.yamlPatch.getDumpSettingsBuilder().setIndicatorIndent(value); }
    /**
     * Don't split long lines
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDontSplitLines() { this.yamlPatch.getDumpSettingsBuilder().setSplitLines(false); }
    /**
     * Max width for literal scalars (default 80)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setWidth(int value) { this.yamlPatch.getDumpSettingsBuilder().setWidth(value); }

    // =============================== End DumpSettingsBuider settings. ===============================

    /**
     * Helper bean for {@link Main#addSet(SetOptions, String, String)}.
     */
    public static
    class SetOptions {

        public SetMode mode = SetMode.ANY;
        public boolean commentOutOriginalEntry;
        public boolean prependMap;

        @CommandLineOption public void existing()    { this.mode = SetMode.EXISTING; }
        @CommandLineOption public void nonExisting() { this.mode = SetMode.NON_EXISTING; }
        @CommandLineOption public void comment()     { this.commentOutOriginalEntry = true; }
        @CommandLineOption public void prependMap()  { this.prependMap = true; }
    }

    /**
     * Add or change one map entry or sequence element.
     * <dl>
     *   <dt>--existing</dt>
     *   <dd>Verify that the map entry resp. sequence element already exists</dd>
     *   <dt>--non-existing</dt>
     *   <dd>Verify that the map entry resp. sequence element does not exist already</dd>
     *   <dt>--comment</dt>
     *   <dd>
     *     Iff this changes an existing map entry, or an existing sequence element, add an end comment to the map resp.
     *     sequence that displays the original map entry resp. sequence element
     *   </dd>
     *   <dt>--prepend-map</dt>
     *   <dd>Add the new map entry at the beginning (instead of to the end)</dd>
     * </dl>
     * 
     * @param setOptions            [ --existing | --non-existing ] [ --comment ] [ --prepend-map ]
     * @param value                 ( <var>yaml-document</var> | {@code @}<var>file-name</var> )
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addSet(SetOptions setOptions, String spec, String value) throws IOException {
        this.yamlPatch.addSet(spec, Main.yamlDocumentOrFile(value), setOptions.mode, setOptions.commentOutOriginalEntry, setOptions.prependMap);
    }

    /**
     * Helper bean for {@link Main#addRemove(RemoveOptions, String)}.
     */
    public static
    class RemoveOptions {

        public RemoveMode mode = RemoveMode.ANY;
        public boolean    commentOutOriginalEntry;

        @CommandLineOption public void existing() { this.mode = RemoveMode.EXISTING; }
        @CommandLineOption public void comment()  { this.commentOutOriginalEntry = true; }
    }

    /**
     * Remove one map entry, sequence element or set member.
     * <dl>
     *   <dt>--existing</dt>
     *   <dd>Verify that the map entry resp. set member already exists</dd>
     *   <dt>--comment</dt>
     *   <dd>Add a comment with the removed map entry, sequence element or set member</dd>
     * </dl>
     * 
     * @param                       removeOptions [ --existing ] [ --comment ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addRemove(RemoveOptions removeOptions, String spec) throws IOException {
        this.yamlPatch.addRemove(spec, removeOptions.mode, removeOptions.commentOutOriginalEntry);
    }

    /**
     * Insert an element into an sequence.
     * 
     * @param yamlDocumentOrFile    ( <var>yaml-document</var> | @<var>file-name</var> )
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addInsert(String spec, String yamlDocumentOrFile) throws IOException {
        this.yamlPatch.addInsert(spec, Main.yamlDocumentOrFile(yamlDocumentOrFile));
    }

    /**
     * Helper bean for {@link Main#addAdd(AddOptions, String)}.
     */
    public static
    class AddOptions {

        public AddMode mode = AddMode.ANY;
        public boolean prepend;

        @CommandLineOption public void nonExisting() { this.mode = AddMode.NON_EXISTING; }
        @CommandLineOption public void prepend()     { this.prepend = true; }
    }

    /**
     * Add a member to a set.
     *
     * <dl>
     *   <dt>--non-existing</dt>
     *   <dd>Verify that the set member does not exist already</dd>
     *   <dt>--prepend</dt>
     *   <dd>Add the new set member at the beginning (instead of to the end)</dd>
     * </dl>
     * 
     * @param addOptions            [ --non-existing ] [ --prepend ]
     * @main.commandLineOptionGroup File-Transformation
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addAdd(AddOptions addOptions, String spec) throws IOException {
        this.yamlPatch.addAdd(spec, addOptions.mode, addOptions.prepend);
    }

    public static Node
    yamlDocumentOrFile(String yamlDocumentOrFile) throws IOException, FileNotFoundException {

        try (Reader r = Main.stringOrFileReader(yamlDocumentOrFile)) {
            return YamlPatch.loadYaml(r);
        }
    }

    private static Reader
    stringOrFileReader(String value) throws FileNotFoundException {
        return (
            value.startsWith("@")
            ? new InputStreamReader(new FileInputStream(value.substring(1)), StandardCharsets.UTF_8)
            : new StringReader(value)
        );
    }

    /**
     * A command line utility that modifies YAML documents.
     * <h2>Usage</h2>
     * <dl>
     *   <dt>{@code yamlpatch} [ <var>option</var> ... ]</dt>
     *   <dd>
     *     Parse a YAML document from STDIN, modify it, and print it to STDOUT.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ... ] !<var>yaml-document</var></dt>
     *   <dd>
     *     Parse the literal <var>YAML-document</var>, modify it, and print it to STDOUT.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file</var></dt>
     *   <dd>
     *     Transform the YAML document in <var>file</var> in-place.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file1</var> <var>file2</var></dt>
     *   <dd>
     *     Read the YAML document in <var>file1</var>, modify it, and write it to (existing or new) <var>file2</var>.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Read the YAML document in each <var>file</var>, modify it, and write it to a file in <var>existing-dir</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options:</h2>
     *
     * <h3>General</h3>
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h3>File processing</h3>
     * <dl>
     * {@main.commandLineOptions File-Processing}
     * </dl>
     *
     * <h3>File transformation</h3>
     * <dl>
     * {@main.commandLineOptions File-Transformation}
     * </dl>
     *
     * <h3>Output generation</h3>
     * <dl>
     * {@main.commandLineOptions Output-Generation}
     * </dl>
     *
     * <h2>Specs</h2>
     * <p>
     *   Many of the options specify a path from the root of the YAML document to a node, as follows:
     * </p>
     * <dl>
     *   <dt>{@code .}<var>identifier</var></dt>
     *   <dt>{@code .(}<var>yaml-document</var>{@code )}</dt>
     *   <dd>Use the map entry with the given key, or the given sequence element, or the given set member.</dd>
     *   <dt>{@code [}<var>0...sequenceSize-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index.</dd>
     *   <dt>{@code [}<var>-sequenceSize...-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index plus <var>sequenceSize</var>.</dd>
     *   <dt>{@code []}</dt>
     *   <dd>The sequence element after the last existing.</dd>
     * </dl>
     */
    public static void
    main(String[] args) throws IOException, CommandLineOptionException {

        // Configure a "Main" object from the command line options.
        Main main = new Main();
        args = CommandLineOptions.parse(args, main);

        if (args.length == 0) {
            
            // Transform YAML document from STDIN to STDOUT.
            main.yamlPatch.contentsTransformer().transform("-", System.in, System.out);
        } else
        if (args.length == 1 && args[0].startsWith("!")) {

            // Parse single command line argument as a YAML document, and transform it to STDOUT.
            main.yamlPatch.transform(new StringReader(args[0].substring(1)), System.out);
        } else
        {

            // Transform a set of files.
            FileTransformations.transform(
                args,                                               // args
                main.yamlPatch.fileTransformer(main.keepOriginals), // fileTransformer
                Mode.TRANSFORM,                                     // mode
                ExceptionHandler.defaultHandler()                   // exceptionHandler
            );
        }
    }
}
