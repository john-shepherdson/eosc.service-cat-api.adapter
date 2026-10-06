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
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Releases of the EOSC-Lot-1 service catalogue specification
 * (https://github.com/EOSC-Lot-1/eosc-service-catalogues-specs) that the adapter can target.
 * The OpenAPI document of each release is bundled in src/main/resources/schemas.
 */
public enum ModelVersion {
    V1_0_0("v1.0.0", "v1"),
    V2_0_0("v2.0.0", "v2");

    private static final String LATEST = "latest";

    private final String label;
    private final String profile;

    ModelVersion(String label, String profile) {
        this.label = label;
        this.profile = profile;
    }

    /** The release tag, for example "v2.0.0". */
    public String label() {
        return label;
    }

    /** Short name used in adapter.properties keys, for example "v2". */
    public String profile() {
        return profile;
    }

    /** The most recent supported release. */
    public static ModelVersion latest() {
        var all = values();
        return all[all.length - 1];
    }

    /** Accepts "latest", "v2.0.0" or "2.0.0" (case-insensitive). */
    public static ModelVersion parse(String text) {
        var t = text.trim().toLowerCase(Locale.ROOT);
        if (LATEST.equals(t)) {
            return latest();
        }
        var wanted = t.startsWith("v") ? t : "v" + t;
        return Arrays.stream(values())
                .filter(v -> v.label.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown model version '" + text
                        + "'. Supported: " + supported()));
    }

    public static String supported() {
        return Arrays.stream(values()).map(ModelVersion::label).collect(Collectors.joining(", "))
                + ", " + LATEST;
    }

    /** Loads the bundled OpenAPI document for this release. */
    public JsonNode loadSchema() throws IOException {
        var path = "/schemas/eosc-lot1-" + label + ".json";
        try (InputStream in = ModelVersion.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Bundled model not found: " + path);
            }
            return new ObjectMapper().readTree(in);
        }
    }
}
