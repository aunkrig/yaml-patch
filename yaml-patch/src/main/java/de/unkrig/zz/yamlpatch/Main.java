
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

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.filetransformation.FileTransformations;
import de.unkrig.commons.file.filetransformation.FileTransformer.Mode;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOption.Cardinality;
import de.unkrig.zz.yamlpatch.YamlPatch.RemoveMode;
import de.unkrig.zz.yamlpatch.YamlPatch.SetMode;

public
class Main {

    private final YamlPatch yamlPatch = new YamlPatch();

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Output formatting; FLOW=single line, BLOCK=each item one a separate line, AUTO: in between
     */
    @CommandLineOption public void
    setFlowStyle(FlowStyle value) { this.yamlPatch.setFlowStyle(value); }

    /**
     * If existing files would be overwritten, keep copies of the originals
     */
    @CommandLineOption public void
    setKeepOriginals() { this.yamlPatch.setKeepOriginals(true); }

    public static
    class SetOptions {

        public SetMode mode = SetMode.ANY;

        /**
         * The map entry or sequence element affected by the operation must exist (and is replaced).
         */
        @CommandLineOption public void setExisting()    { this.mode = SetMode.EXISTING; }
        
        /**
         * The map entry or sequence element affected by the operation must not exist (and is created).
         */
        @CommandLineOption public void setNonExisting() { this.mode = SetMode.NON_EXISTING; }
    }

    /**
     * Puts an entry into a set, or changes one sequence element.
     * 
     * @param value      ( <var>yaml-document</var> | {@code @}<var>file-name</var> )
     * @param setOptions [ --existing | --non-existing ]
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addSet(SetOptions setOptions, String spec, String value) throws IOException {
        this.yamlPatch.addSet(spec, Main.yamlDocumentOrFile(value), setOptions.mode);
    }

    public static
    class RemoveOptions {

        public RemoveMode mode = RemoveMode.ANY;

        /**
         * The specified map entry must exist.
         */
        @CommandLineOption public void setExisting() { this.mode = RemoveMode.EXISTING; }
    }

    /**
     * Removes one sequence element, map entry or set member.
     * 
     * @param removeOptions [ --existing ]
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addRemove(RemoveOptions removeOptions, String spec) throws IOException {
        this.yamlPatch.addRemove(spec, removeOptions.mode);
    }

    /**
     * Inserts an element into an sequence.
     * 
     * @param yamlDocumentOrFile ( <var>yaml-document</var> | '@'<var>file-name</var> )
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addInsert(String spec, String yamlDocumentOrFile) throws IOException {
        this.yamlPatch.addInsert(spec, Main.yamlDocumentOrFile(yamlDocumentOrFile));
    }
    
    /**
     * Adds a member to a set.
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addAdd(String spec) throws IOException {
        this.yamlPatch.addAdd(spec);
    }

    public static Object
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
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file-or-dir</var></dt>
     *   <dd>
     *     Transforms <var>file-or-dir</var> in-place.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file-or-dir</var> <var>new-file-or-dir</var></dt>
     *   <dd>
     *     Read the YAML documents in <var>file-or-dir</var>, modify them, and write them to <var>new-file-or-dir</var>.
     *   </dd>
     *   <dt>{@code yamlpatch} [ <var>option</var> ] <var>file-or-dir</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Read the YAML documents in <var>file-or-dir</var>, modify them, and write them to files/dirs in <var>existing-dir</var>.
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h2>Specs</h2>
     * <p>
     *   Many of the options specify a path from the root of the YAML document to a node, as follows:
     * </p>
     * <dl>
     *   <dt>{@code .}<var>identifier</var></dt>
     *   <dt>{@code .(}<var>yaml-document</var>{@code )}</dt>
     *   <dd>Use the given map entry.</dd>
     *   <dt>{@code [}<var>0...sequenceSize-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index index <var>n</var>.</dd>
     *   <dt>{@code [}<var>-sequenceSize...-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index plus <var>sequenceSize</var>.</dd>
     *   <dt><code>{</code><var>yaml-document</var><code>}</code></dt>
     *   <dd>Use the given set member.</dd>
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
                args,                             // args
                main.yamlPatch.fileTransformer(), // fileTransformer
                Mode.TRANSFORM,                   // mode
                ExceptionHandler.defaultHandler() // exceptionHandler
            );
        }
    }
}