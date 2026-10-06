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

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SpecVersion;

/** Validates bundles against ServiceBundle in the target OpenAPI document. */
public final class BundleValidator {

    private static final String COMPONENTS = "components";

    private final JsonSchema schema;

    public BundleValidator(JsonNode openApi) {
        var root = openApi.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).removeAll();
        var wrapper = (com.fasterxml.jackson.databind.node.ObjectNode) root;
        wrapper.put("$ref", "#/components/schemas/ServiceBundle");
        wrapper.set(COMPONENTS, openApi.get(COMPONENTS));
        // "components" only holds the definitions reached through $ref; it is not a validation keyword.
        var metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
                .keyword(new NonValidationKeyword(COMPONENTS))
                .build();
        var factory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
                .metaSchema(metaSchema)
                .build();
        this.schema = factory.getSchema(wrapper);
    }

    /** Returns validation messages; empty means the bundle is compliant. */
    public List<String> validate(JsonNode bundle) {
        return schema.validate(bundle).stream().map(m -> m.getMessage()).sorted().toList();
    }
}
