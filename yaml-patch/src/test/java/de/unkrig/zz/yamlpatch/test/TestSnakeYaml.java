
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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

public
class TestSnakeYaml {

    @Test public void
    testLoading() throws Exception {

        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();
        Load load = new Load(settings);

        @SuppressWarnings("unchecked") Map<String, Object>
        map = (Map<String, Object>) load.loadFromString(
            ""
            + "a: 1\n"
            + "b: 2\n"
            + "c:\n"
            + "  - aaa\n"
            + "  - bbb\n"
            + "d: [ 7, 8, 9 ]\n"
            + "e: { 2: 3, 3.0: 5.0, nulL: trUE }\n"
        );
    
        Assert.assertEquals(mapOf(
            "a", 1,
            "b", 2,
            "c", listOf("aaa", "bbb"),
            "d", listOf(7, 8, 9),
            "e", mapOf(
                2, 3,
                3.0, 5.0,
                "nulL", "trUE"
            )
        ), map);
    }

    @Test public void
    testDumping() {
        
        DumpSettings dumpSettings = (
            DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .build()
        );
        Dump dump = new Dump(dumpSettings);
        String output = dump.dumpToString(mapOf(
            "x", 1,
            "y", 2,
            "z", 3,
            "a", mapOf(
                "a", "ALPHA",
                "b", listOf(
                    "c",
                    4,
                    mapOf(
                        true,     new byte[] { 1, 2, 3 },
                        1.000,    2.000f,
                        (byte) 3, (short) 4,
                        "foo",    "bar"
                    )
                )
            )
        ));
        Assert.assertEquals((
            ""
            + "x: 1\n" 
            + "y: 2\n" 
            + "z: 3\n" 
            + "a:\n" 
            + "  a: ALPHA\n" 
            + "  b:\n" 
            + "  - c\n" 
            + "  - 4\n" 
            + "  - true: !!binary |-\n" 
            + "      AQID\n" 
            + "    1.0: 2.0\n" 
            + "    3: 4\n" 
            + "    foo: bar\n" 
        ), output);
    }

    @Test public void
    testModifying() {
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();
        Load load = new Load(settings);

        @SuppressWarnings("unchecked") Map<String, Object>
        map = (Map<String, Object>) load.loadFromString(
            ""
            + "a: 1\n"
            + "77: \"2\"\n"
            + "c:\n"
            + "  - aaa\n"
            + "  - bbb\n"
            + "d: [ 7, 8, 9 ]\n"
            + "e: { 2: 3, 3.0: 5.0, nulL: trUE }\n"
        );

        DumpSettings dumpSettings = (
            DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .build()
        );
        Dump dump = new Dump(dumpSettings);
        String output = dump.dumpToString(map);

        Assert.assertEquals((
            ""
            + "a: 1\n" 
            + "77: '2'\n" 
            + "c:\n" 
            + "- aaa\n" 
            + "- bbb\n" 
            + "d:\n" 
            + "- 7\n" 
            + "- 8\n" 
            + "- 9\n" 
            + "e:\n" 
            + "  2: 3\n" 
            + "  3.0: 5.0\n" 
            + "  nulL: trUE\n" 
        ), output);
    }

    private static <K, V> Object
    mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }

    private static <K, V> Object
    mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        return result;
    }
    
    private static <K, V> Object
    mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        return result;
    }

    private static <K, V> Object
    mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        result.put(k4, v4);
        result.put(k5, v5);
        return result;
    }

    @SafeVarargs private static <T> List<T>
    listOf(T... elements) {
        return Arrays.asList(elements);
    }
}
