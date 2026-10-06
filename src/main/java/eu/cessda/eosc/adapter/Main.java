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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Command line entry point. */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String USAGE = """
            Usage: java -jar eosc-adapter.jar --source <url|file> [options]
              --source  <url|file>  EOSC Beyond service list (required)
              --model-version <v>   EOSC-Lot-1 model release: %s
                                    (default: latest)
              --config  <file>      Properties overriding adapter.properties
              --output  <file>      Output file name (default: services.json). A relative name is
                                    placed in the outputs directory; an absolute path is used as is
              --outdir  <dir>       Outputs directory (default: outputs)
              --strict              Exit with status 2 if any service was rejected
            """.formatted(ModelVersion.supported());

    private Main() {}

    public static void main(String[] args) throws Exception {
        String source = null;
        var modelVersion = ModelVersion.latest();
        Path config = null;
        Path output = Path.of("services.json");
        Path outDir = Path.of("outputs");
        boolean strict = false;
        var it = java.util.Arrays.asList(args).iterator();
        while (it.hasNext()) {
            switch (it.next()) {
                case "--source" -> source = value(it);
                case "--model-version" -> modelVersion = parseVersion(value(it));
                case "--config" -> config = Path.of(value(it));
                case "--output" -> output = Path.of(value(it));
                case "--outdir" -> outDir = Path.of(value(it));
                case "--strict" -> strict = true;
                default -> usageAndExit();
            }
        }
        if (source == null) {
            usageAndExit();
        }

        var om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        LOG.info("Targeting the EOSC-Lot-1 service catalogue model {}", modelVersion.label());
        var adapter = new Adapter(AdapterConfig.load(config), modelVersion);
        var result = adapter.convert(read(om, source));

        var target = output.isAbsolute() ? output : outDir.resolve(output);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, om.writeValueAsString(result.response()));
        LOG.info("Wrote {}", target);
        LOG.info("Converted {} service(s), rejected {}",
                result.response().get("results").size(), result.rejected().size());
        result.rejected().forEach((id, errs) -> LOG.warn("REJECTED {}: {}", id, String.join("; ", errs)));
        if (strict && !result.rejected().isEmpty()) {
            System.exit(2);
        }
    }

    /** Returns the value following an option, or prints the usage and exits if it is missing. */
    private static String value(java.util.Iterator<String> it) {
        if (!it.hasNext()) {
            usageAndExit();
        }
        return it.next();
    }

    private static ModelVersion parseVersion(String text) {
        try {
            return ModelVersion.parse(text);
        } catch (IllegalArgumentException e) {
            LOG.error(e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static void usageAndExit() {
        LOG.error("{}", USAGE);
        System.exit(1);
    }

    static JsonNode read(ObjectMapper om, String location) throws IOException, InterruptedException {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            var req = HttpRequest.newBuilder(URI.create(location)).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json").build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new IOException("GET " + location + " returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body()) {
                return om.readTree(in);
            }
        }
        return om.readTree(Path.of(location).toFile());
    }
}
