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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Converts a whole EOSC Beyond response into a validated PagingServiceBundle. */
public final class Adapter {

    /** The converted document plus the ids (with messages) of services that were excluded. */
    public record Result(ObjectNode response, Map<String, java.util.List<String>> rejected) {}

    private final ObjectMapper om = new ObjectMapper();
    private final ServiceMapper mapper;
    private final BundleValidator validator;

    public Adapter(AdapterConfig config, ModelVersion version) throws IOException {
        this.mapper = new ServiceMapper(config, version);
        this.validator = new BundleValidator(version.loadSchema());
    }

    public Result convert(JsonNode source) {
        // Beyond wraps services in {total, from, to, results}; tolerate a bare array too.
        var items = source.isArray() ? source : source.path("results");
        var out = om.createObjectNode();
        var results = om.createArrayNode();
        var rejected = new LinkedHashMap<String, java.util.List<String>>();
        int n = 0;
        for (var item : items) {
            n++;
            var bundle = mapper.map(item);
            var errors = validator.validate(bundle);
            if (errors.isEmpty()) {
                results.add(bundle);
            } else {
                rejected.put(item.path("id").asText("#" + n), errors);
            }
        }
        out.put("total", results.size());
        out.put("from", 0);
        out.put("to", results.size());
        out.set("results", results);
        return new Result(out, rejected);
    }
}
