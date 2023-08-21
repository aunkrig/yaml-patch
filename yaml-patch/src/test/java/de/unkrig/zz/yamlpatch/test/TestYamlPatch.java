
/*
 * de.unkrig.zz.yamlpatch - Modifying YAML documents
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

package de.unkrig.zz.yamlpatch.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import de.unkrig.zz.yamlpatch.SpecSyntaxException;
import de.unkrig.zz.yamlpatch.YamlPatch;
import de.unkrig.zz.yamlpatch.YamlPatch.RemoveMode;
import de.unkrig.zz.yamlpatch.YamlPatch.SetMode;

public
class TestYamlPatch {

    private static final String INPUT = (
        ""
        + "a: b\n"
        + "c: d\n"
        + "# Hash Comment\n"
        + "e:\n"
        + "- f\n"
        + "- g\n"
        + "h:\n"
        + "  i: !!set\n"
        + "    ? j\n"
        + "    ? 7\n"
        + "    ?\n"
        + "      k: l\n"
        + "      \"x)x\" : n\n"
    );

    @Test public void
    testNop() throws Exception {
        
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
        ), new YamlPatch());
    }
    
    @Test public void
    testRemoveMapEntry1() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addRemove(".h.i", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h: {}\n"
            + "# i: !!set\n"
            + "#   j:\n"
            + "#   7:\n"
            + "#   ? k: l\n"
            + "#     \"x)x\": n\n"
            + "#   :\n"
        ), yamlPatch);
    }
    
    @Test public void
    testRemoveMapEntry2() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addRemove(".h.(\"i\")", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h: {}\n"
            + "# i: !!set\n"
            + "#   j:\n"
            + "#   7:\n"
            + "#   ? k: l\n"
            + "#     \"x)x\": n\n"
            + "#   :\n"
        ), yamlPatch);
    }

    @Test public void
    testRemoveSetMember1() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addRemove(".h.i(\"7\")", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
            + "  # 7:\n"
        ), yamlPatch);
    }

    @Test public void
    testRemoveSetMember2() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addRemove(".h.i({k: l, x)x: n})", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "  # ? k: l\n"
            + "  #   \"x)x\": n\n"
            + "  # :\n"
        ), yamlPatch);
    }
    
    @Test public void
    testRemoveSequenceElement() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addRemove(".e[0]", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- g\n"
            + "# - f\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
        ), yamlPatch);
        yamlPatch.addRemove(".e[0]", RemoveMode.EXISTING, true);
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e: []\n"
            + "# - f\n"
            + "# - g\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
        ), yamlPatch);
    }

    @Test public void
    testChangeMapEntry() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addSet(
            ".c",
            new ScalarNode(Tag.STR, "ddd", ScalarStyle.PLAIN),
            SetMode.EXISTING,
            true, // commentOutOriginalEntry
            false // prependMap
        );
        assertMain((
            ""
            + "a: b\n"
            + "# c: d\n"
            + "c: ddd\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- f\n"
            + "- g\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
        ), yamlPatch);
    }
    
    @Test public void
    testSequenceElement() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.getDumpSettingsBuilder().setDumpComments(true);
        yamlPatch.addSet(
            ".e[0]",
            new ScalarNode(Tag.STR, "fff", ScalarStyle.PLAIN),
            SetMode.EXISTING,
            true, // commentOutOriginalEntry
            false // prependMap
        );
        assertMain((
            ""
            + "a: b\n"
            + "c: d\n"
            + "# Hash Comment\n"
            + "e:\n"
            + "- fff\n"
            + "- g\n"
            + "# - f\n"
            + "h:\n"
            + "  i: !!set\n"
            + "    j:\n"
            + "    7:\n"
            + "    ? k: l\n"
            + "      \"x)x\": n\n"
            + "    :\n"
            ), yamlPatch);
    }
    
    @Test(expected = SpecSyntaxException.class) public void
    testNonTerminalSetMember() throws Exception {
        
        YamlPatch yamlPatch = new YamlPatch();
        yamlPatch.addRemove(".h.i({k: l, x)x: n}).x", RemoveMode.EXISTING, true);
        assertMain("", yamlPatch);
    }

    private void
    assertMain(String expected, YamlPatch yamlPatch) throws Exception {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream bais = new ByteArrayInputStream(INPUT.getBytes(StandardCharsets.UTF_8));
        yamlPatch.contentsTransformer().transform("", bais, baos);
        Assert.assertEquals(expected, baos.toString());
    }
}
