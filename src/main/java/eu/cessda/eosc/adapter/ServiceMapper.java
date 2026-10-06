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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Converts one EOSC Beyond service description into an EOSC Federation EOSCServiceBundle. */
public final class ServiceMapper {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String TRL = "trl";
    private static final String ORDER_TYPE = "orderType";
    private static final String ACCESS_MODES = "accessModes";
    /** Value of default.tagline meaning "use the service name". */
    private static final String TAGLINE_FROM_NAME = "name";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    private final ObjectMapper om = new ObjectMapper();
    private final AdapterConfig config;

    public ServiceMapper(AdapterConfig config) {
        this.config = config;
    }

    public ObjectNode map(JsonNode src) {
        var s = om.createObjectNode();
        var id = text(src, ID);
        putIfPresent(s, ID, id);
        putArrayIfAny(s, "alternativeIdentifiers", alternativeIdentifiers(src), true);
        putIfPresent(s, "abbreviation", text(src, "abbreviation"));
        putIfPresent(s, NAME, text(src, NAME));
        putIfPresent(s, "webpage", firstNonNull(text(src, "webpage"), firstText(src.path("urls"))));
        putIfPresent(s, "description", description(src));
        putIfPresent(s, "tagline", tagline(src));
        putIfPresent(s, "logo", text(src, "logo"));
        s.set("scientificDomains", pairs(src.path("scientificDomains"),
                "scientificDomain", "scientificSubdomain", "scientificDomain", "scientificSubdomain"));
        s.set("categories", pairs(src.path("categories"),
                "category", "subcategory", "category", "subcategory"));
        s.set("targetUsers", strings(config.list("default.targetUsers")));
        putArrayIfAny(s, ACCESS_MODES, accessModes(src), false);
        s.set("tags", tags(src.path("tags")));
        s.set("languageAvailabilities", strings(config.list("default.languageAvailabilities")));
        putIfPresent(s, "helpdeskEmail", helpdesk(src));
        putIfPresent(s, "securityContactEmail", null);
        putIfPresent(s, TRL, mapped(TRL, text(src, TRL)));
        putIfPresent(s, "userManual", text(src, "userManual"));
        putIfPresent(s, "termsOfUse", text(src, "termsOfUse"));
        putIfPresent(s, "privacyPolicy", text(src, "privacyPolicy"));
        putIfPresent(s, "accessPolicy", text(src, "accessPolicy"));
        putIfPresent(s, ORDER_TYPE, mapped(ORDER_TYPE, text(src, ORDER_TYPE)));

        var bundle = om.createObjectNode();
        putIfPresent(bundle, ID, id);
        bundle.set("service", s);
        return bundle;
    }

    // ---- field helpers -------------------------------------------------------------------

    private String description(JsonNode src) {
        var d = text(src, "description");
        if (d == null || !config.flag("description.stripHtml")) {
            return d;
        }
        var plain = TAGS.matcher(d).replaceAll(" ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
        return plain.isEmpty() ? null : plain;
    }

    private String tagline(JsonNode src) {
        var explicit = text(src, "tagline");
        if (explicit != null) {
            return explicit;
        }
        return TAGLINE_FROM_NAME.equals(config.get("default.tagline")) ? text(src, NAME) : config.get("default.tagline");
    }

    private String helpdesk(JsonNode src) {
        var explicit = text(src, "helpdeskEmail");
        if (explicit != null) {
            return explicit;
        }
        for (var c : src.path("publicContacts")) {
            if (c.isTextual() && EMAIL.matcher(c.asText().trim()).matches()) {
                return c.asText().trim();
            }
        }
        return null;
    }

    private List<JsonNode> alternativeIdentifiers(JsonNode src) {
        var out = new ArrayList<JsonNode>();
        var seen = new LinkedHashSet<String>();
        var pids = src.path("alternativePIDs");
        if (pids.isArray()) {
            for (var p : pids) {
                String type = "other";
                String value = null;
                if (p.isTextual()) {
                    value = p.asText();
                } else if (p.isObject()) {
                    // Beyond's shape is undocumented for non-null values; accept the usual spellings.
                    value = firstNonNull(text(p, "value"), text(p, "pid"), text(p, "PID"), text(p, "identifier"));
                    type = firstNonNull(text(p, "type"), text(p, "scheme"), text(p, "pidScheme"),
                            text(p, "PIDScheme"), "other");
                }
                if (value != null && seen.add(type + "|" + value)) {
                    out.add(identifier(type.toLowerCase(), value));
                }
            }
        }
        return out;
    }

    private ObjectNode identifier(String type, String value) {
        var n = om.createObjectNode();
        n.put("type", type);
        n.put("value", value);
        return n;
    }

    private List<JsonNode> accessModes(JsonNode src) {
        var raw = src.hasNonNull(ACCESS_MODES) ? src.get(ACCESS_MODES) : src.path("accessTypes");
        var out = new ArrayList<JsonNode>();
        var seen = new LinkedHashSet<String>();
        for (var v : asList(raw)) {
            var m = config.map("accessMode", v);
            if (m != null && seen.add(m)) {
                out.add(om.getNodeFactory().textNode(m));
            }
        }
        return out;
    }

    private ArrayNode pairs(JsonNode arr, String srcKey, String srcSubKey, String field, String subField) {
        var out = om.createArrayNode();
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (var e : arr) {
            var main = mapped(field, text(e, srcKey));
            if (main == null) {
                continue;
            }
            var n = om.createObjectNode();
            n.put(srcKey, main);
            var sub = mapped(subField, text(e, srcSubKey));
            if (sub != null) {
                n.put(srcSubKey, sub);
            }
            out.add(n);
        }
        return out;
    }

    private ArrayNode tags(JsonNode arr) {
        var out = om.createArrayNode();
        for (var t : asList(arr)) {
            if (!t.isBlank()) {
                out.add(t.trim());
            }
        }
        return out;
    }

    private ArrayNode strings(List<String> values) {
        var out = om.createArrayNode();
        values.forEach(out::add);
        return out;
    }

    private String mapped(String field, String value) {
        return value == null ? null : config.map(field, value);
    }

    /** Accepts a string, an array of strings, or null. */
    private static List<String> asList(JsonNode n) {
        var out = new ArrayList<String>();
        if (n == null || n.isNull()) {
            return out;
        }
        if (n.isArray()) {
            n.forEach(i -> {
                if (i.isTextual()) {
                    out.add(i.asText());
                }
            });
        } else if (n.isTextual()) {
            out.add(n.asText());
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        var v = n.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            return null;
        }
        var s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstText(JsonNode arr) {
        var l = asList(arr);
        return l.isEmpty() ? null : l.get(0);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (var v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** Optional fields are omitted rather than emitted as null (the target model forbids null arrays). */
    private static void putIfPresent(ObjectNode n, String field, String value) {
        if (value != null) {
            n.put(field, value);
        }
    }

    private static void putArrayIfAny(ObjectNode n, String field, List<JsonNode> values, boolean emitEmpty) {
        if (values.isEmpty() && !emitEmpty) {
            return;
        }
        var a = n.putArray(field);
        values.forEach(a::add);
    }
}
