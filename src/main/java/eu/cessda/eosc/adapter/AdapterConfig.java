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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Adapter settings: classpath defaults optionally overlaid by an external properties file. */
public final class AdapterConfig {

    private final Properties props;

    private AdapterConfig(Properties props) {
        this.props = props;
    }

    public static AdapterConfig load(Path override) throws IOException {
        var props = new Properties();
        try (InputStream in = AdapterConfig.class.getResourceAsStream("/adapter.properties")) {
            if (in != null) {
                try (Reader r = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                    props.load(r);
                }
            }
        }
        if (override != null) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                props.load(r);
            }
        }
        return new AdapterConfig(props);
    }

    public static AdapterConfig defaults() {
        try {
            return load(null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String get(String key) {
        var v = props.getProperty(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public List<String> list(String key) {
        var v = get(key);
        if (v == null) {
            return List.of();
        }
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Applies map.&lt;field&gt;.&lt;value&gt;. Returns the value unchanged when no entry exists,
     * or null when the entry exists but is empty (value dropped).
     */
    public String map(String field, String value) {
        var key = "map." + field + "." + value;
        if (!props.containsKey(key)) {
            return value;
        }
        return get(key);
    }

    /**
     * Like {@link #map} but returns null when there is no entry (or the entry is empty), so the
     * caller can fall back to another table.
     */
    public String mapIfPresent(String field, String value) {
        return get("map." + field + "." + value);
    }

    /** All entries of one mapping table: map.&lt;field&gt;.&lt;sourceValue&gt; to targetValue. */
    public java.util.Map<String, String> table(String field) {
        var prefix = "map." + field + ".";
        var out = new java.util.TreeMap<String, String>();
        props.stringPropertyNames().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(k -> out.put(k.substring(prefix.length()), props.getProperty(k).trim()));
        return out;
    }
}
