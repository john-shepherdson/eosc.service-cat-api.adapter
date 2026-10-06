/*
 * SPDX-FileCopyrightText: 2026 CESSDA ERIC (support@cessda.eu)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package eu.cessda.eosc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdapterTest {

    static final ObjectMapper OM = new ObjectMapper();
    static BundleValidator validator;
    static JsonNode source;

    static JsonNode resource(String name) throws IOException {
        try (InputStream in = AdapterTest.class.getResourceAsStream("/" + name)) {
            return OM.readTree(in);
        }
    }

    @BeforeAll
    static void load() throws IOException {
        validator = new BundleValidator(resource("openapi.json"));
        source = resource("beyond-services.json");
    }

    @Test
    void allStagingServicesConvertAndValidate() {
        var result = new Adapter(AdapterConfig.defaults(), validator).convert(source);
        assertTrue(result.rejected().isEmpty(), () -> "rejected: " + result.rejected());
        assertEquals(7, result.response().get("results").size());
        assertEquals(7, result.response().get("total").asInt());
    }

    @Test
    void mapsFieldsAndAppliesDefaults() {
        var bundle = new ServiceMapper(AdapterConfig.defaults()).map(source.get("results").get(0));
        var s = bundle.get("service");
        assertEquals("service/S1Ux8C00", bundle.get("id").asText());
        assertEquals("CESSDA Data Catalogue", s.get("tagline").asText(), "tagline defaults to name");
        assertEquals("en", s.get("languageAvailabilities").get(0).asText());
        assertEquals(3, s.get("targetUsers").size());
        assertEquals("support@cessda.eu", s.get("helpdeskEmail").asText());
        assertEquals(0, s.get("alternativeIdentifiers").size(), "nodePID identifies the node, not the service");
        assertFalse(s.has("accessModes"), "access_type-virtual has no access mode equivalent");
        assertFalse(s.get("description").asText().contains("<p>"), "HTML stripped");
    }

    @Test
    void neverEmitsNulls() {
        var bundle = new ServiceMapper(AdapterConfig.defaults()).map(source.get("results").get(1));
        assertNoNulls(bundle);
    }

    @Test
    void invalidServiceIsRejectedNotEmitted() throws IOException {
        var broken = (com.fasterxml.jackson.databind.node.ObjectNode) source.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) broken.get("results").get(0)).put("trl", "trl-99");
        var result = new Adapter(AdapterConfig.defaults(), validator).convert(broken);
        assertEquals(1, result.rejected().size());
        assertEquals(6, result.response().get("results").size());
    }

    @Test
    void serviceWithNullCategoriesIsRejected() {
        var broken = source.deepCopy();
        var victim = (com.fasterxml.jackson.databind.node.ObjectNode) broken.get("results").get(2);
        victim.putNull("categories");
        assertCategoriesRejected(broken, victim.get("id").asText());
    }

    @Test
    void serviceWithMissingCategoriesIsRejected() {
        var broken = source.deepCopy();
        var victim = (com.fasterxml.jackson.databind.node.ObjectNode) broken.get("results").get(2);
        victim.remove("categories");
        assertCategoriesRejected(broken, victim.get("id").asText());
    }

    private static void assertCategoriesRejected(JsonNode broken, String id) {
        var result = new Adapter(AdapterConfig.defaults(), validator).convert(broken);
        assertEquals(java.util.Set.of(id), result.rejected().keySet());
        assertTrue(result.rejected().get(id).stream().anyMatch(m -> m.contains("categories")),
                () -> "messages: " + result.rejected().get(id));
        assertEquals(6, result.response().get("results").size());
    }

    private static void assertNoNulls(JsonNode n) {
        assertFalse(n.isNull());
        n.forEach(AdapterTest::assertNoNulls);
    }
}
