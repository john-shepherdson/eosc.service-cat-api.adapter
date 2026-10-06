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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AdapterTest {

    static final ObjectMapper OM = new ObjectMapper();
    static JsonNode source;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = AdapterTest.class.getResourceAsStream("/beyond-services.json")) {
            source = OM.readTree(in);
        }
    }

    static Adapter adapter(ModelVersion version) throws IOException {
        return new Adapter(AdapterConfig.defaults(), version);
    }

    @ParameterizedTest
    @EnumSource(ModelVersion.class)
    void allStagingServicesConvertAndValidate(ModelVersion version) throws IOException {
        var result = adapter(version).convert(source);
        assertTrue(result.rejected().isEmpty(), () -> "rejected: " + result.rejected());
        assertEquals(7, result.response().get("results").size());
        assertEquals(7, result.response().get("total").asInt());
    }

    @ParameterizedTest
    @EnumSource(ModelVersion.class)
    void mapsFieldsAndAppliesDefaults(ModelVersion version) {
        var bundle = new ServiceMapper(AdapterConfig.defaults(), version).map(source.get("results").get(0));
        var s = bundle.get("service");
        assertEquals("service/S1Ux8C00", bundle.get("id").asText());
        assertEquals("CESSDA Data Catalogue", s.get("tagline").asText(), "tagline defaults to name");
        assertEquals("en", s.get("languageAvailabilities").get(0).asText());
        assertEquals(3, s.get("targetUsers").size());
        assertEquals("support@cessda.eu", s.get("helpdeskEmail").asText());
        assertEquals(0, s.get("alternativeIdentifiers").size(), "nodePID identifies the node, not the service");
        assertFalse(s.has("accessModes"), "access_type-virtual has no access mode equivalent");
        assertFalse(s.get("description").asText().contains("<p>"), "HTML stripped");
        assertFalse(s.get("scientificDomains").get(0).has("scientificSubdomain"), "model has no subdomain");
        assertFalse(s.get("categories").get(0).has("subcategory"), "model has no subcategory");
    }

    @Test
    void v1KeepsTheBeyondCategoryVocabulary() {
        var s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V1_0_0)
                .map(source.get("results").get(0)).get("service");
        assertEquals("category-aggregators_and_integrators-aggregators_and_integrators",
                s.get("categories").get(0).get("category").asText());
    }

    @Test
    void v2TranslatesCategoriesToServiceClassification() {
        var s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V2_0_0)
                .map(source.get("results").get(0)).get("service");
        assertEquals("service_classification-publishing_discovery",
                s.get("categories").get(0).get("category").asText());
    }

    @Test
    void v2SubcategoryDecidesBeforeCategory() {
        // Vocabulary Service: data_management category, "discovery" subcategory.
        var s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V2_0_0)
                .map(source.get("results").get(4)).get("service");
        assertEquals("service_classification-publishing_discovery",
                s.get("categories").get(0).get("category").asText());
    }

    @Test
    void v2FallsBackToCategoryWhenSubcategoryHasNoEntry() {
        var copy = source.get("results").get(4).deepCopy();
        ((ObjectNode) copy.get("categories").get(0)).put("subcategory",
                "subcategory-processing_and_analysis-data_management-annotation");
        var s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V2_0_0).map(copy).get("service");
        assertEquals("service_classification-data_management_curation",
                s.get("categories").get(0).get("category").asText());
        ((ObjectNode) copy.get("categories").get(0)).putNull("subcategory");
        s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V2_0_0).map(copy).get("service");
        assertEquals("service_classification-data_management_curation",
                s.get("categories").get(0).get("category").asText());
    }

    @Test
    void v1IgnoresSubcategories() {
        var s = new ServiceMapper(AdapterConfig.defaults(), ModelVersion.V1_0_0)
                .map(source.get("results").get(4)).get("service");
        assertEquals("category-processing_and_analysis-data_management",
                s.get("categories").get(0).get("category").asText());
    }

    @Test
    void v2SubcategoryOverridesTargetValidCategories() throws IOException {
        var allowed = new java.util.HashSet<String>();
        ModelVersion.V2_0_0.loadSchema().at("/components/schemas/Category/enum")
                .forEach(n -> allowed.add(n.asText()));
        var table = AdapterConfig.defaults().table("subcategory.v2");
        assertFalse(table.isEmpty());
        table.forEach((sub, target) ->
                assertTrue(allowed.contains(target), () -> sub + " maps to invalid '" + target + "'"));
    }

    @Test
    void v2CategoryMappingCoversEveryV1Category() throws IOException {
        var config = AdapterConfig.defaults();
        var v1 = ModelVersion.V1_0_0.loadSchema().at("/components/schemas/Category/enum");
        var v2 = ModelVersion.V2_0_0.loadSchema().at("/components/schemas/Category/enum");
        var allowed = new java.util.HashSet<String>();
        v2.forEach(n -> allowed.add(n.asText()));
        assertEquals(20, v1.size());
        for (var c : v1) {
            var mapped = config.map("category.v2", c.asText());
            assertTrue(allowed.contains(mapped), () -> c.asText() + " maps to invalid '" + mapped + "'");
        }
    }

    @ParameterizedTest
    @EnumSource(ModelVersion.class)
    void neverEmitsNulls(ModelVersion version) {
        var bundle = new ServiceMapper(AdapterConfig.defaults(), version).map(source.get("results").get(1));
        assertNoNulls(bundle);
    }

    @ParameterizedTest
    @EnumSource(ModelVersion.class)
    void invalidServiceIsRejectedNotEmitted(ModelVersion version) throws IOException {
        var broken = source.deepCopy();
        ((ObjectNode) broken.get("results").get(0)).put("trl", "trl-99");
        var result = adapter(version).convert(broken);
        assertEquals(1, result.rejected().size());
        assertEquals(6, result.response().get("results").size());
    }

    @ParameterizedTest
    @EnumSource(ModelVersion.class)
    void serviceWithUnknownCategoryIsRejected(ModelVersion version) throws IOException {
        var broken = source.deepCopy();
        var victim = (ObjectNode) broken.get("results").get(2);
        ((ObjectNode) victim.get("categories").get(0)).put("category", "category-not-in-any-model");
        var result = adapter(version).convert(broken);
        var id = victim.get("id").asText();
        assertEquals(Set.of(id), result.rejected().keySet());
        assertTrue(result.rejected().get(id).stream().anyMatch(m -> m.contains("categories")),
                () -> "messages: " + result.rejected().get(id));
        assertEquals(6, result.response().get("results").size());
    }

    @Test
    void modelVersionParsing() {
        assertEquals(ModelVersion.V2_0_0, ModelVersion.latest());
        assertEquals(ModelVersion.latest(), ModelVersion.parse("latest"));
        assertEquals(ModelVersion.V1_0_0, ModelVersion.parse("v1.0.0"));
        assertEquals(ModelVersion.V1_0_0, ModelVersion.parse("1.0.0"));
        assertEquals(ModelVersion.V2_0_0, ModelVersion.parse(" V2.0.0 "));
        var e = assertThrows(IllegalArgumentException.class, () -> ModelVersion.parse("v3.0.0"));
        assertTrue(e.getMessage().contains("v1.0.0") && e.getMessage().contains("v2.0.0"));
    }

    private static void assertNoNulls(JsonNode n) {
        assertFalse(n.isNull());
        n.forEach(AdapterTest::assertNoNulls);
    }
}
