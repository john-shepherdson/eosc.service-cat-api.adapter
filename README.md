[![SQA badge](https://api.eu.badgr.io/public/assertions/<SQAaaS image ID>/image)](https://api.eu.badgr.io/public/badges/<SQAaaS badge ID>)

# EOSC Service Catalogue API Adapter

This repository contains the source code for a Java adapter that converts service descriptions from the
[EOSC Beyond Service Catalogue API](https://service-catalogue-staging.beyond.cessda.eu/api/service/all)
into JSON that complies with the EOSC Federation service model
([OpenAPI](https://srvcat.cloud.cesnet.cz/openapi.json)).

Each converted service is validated against the `EOSCServiceBundle` schema in the target OpenAPI document.
Services that fail validation are excluded from the output and reported with the reasons.

## Prerequisites

- Java 21
- Maven 3.9+

## Quick Start

1. Check prerequisites and install any required software.
1. Clone the repository to your local workspace.
1. Build the executable jar (this also runs the tests):

   ```bash
   mvn package
   ```

1. Run the adapter:

   ```bash
   java -jar target/eosc-adapter-0.1.0-SNAPSHOT.jar \
     --source https://service-catalogue-staging.beyond.cessda.eu/api/service/all \
     --output services.json
   ```

### Command line options

| Option                 | Description                                                                                                                |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `--source <url\|file>` | EOSC Beyond service list (required)                                                                                        |
| `--schema <url\|file>` | Target OpenAPI document (default: `https://srvcat.cloud.cesnet.cz/openapi.json`)                                           |
| `--config <file>`      | Properties file overriding the built-in defaults                                                                           |
| `--output <file>`      | Output file name (default: `services.json`). A relative name goes in the outputs directory; an absolute path is used as is |
| `--outdir <dir>`       | Outputs directory (default: `outputs`)                                                                                     |
| `--strict`             | Exit with status 2 if any service was rejected                                                                             |

The result is written to `outputs/services.json` by default. The contents of `outputs/` are excluded from commits
by `.gitignore`. A summary and any rejected services are written to stderr.

## Project Structure

```text
pom.xml
src/main/java/eu/cessda/eosc/adapter/
  Main.java              Command line entry point
  Adapter.java           Converts a whole response and validates each service
  ServiceMapper.java     Maps one EOSC Beyond service to an EOSC Federation bundle
  BundleValidator.java   JSON Schema validation against the target OpenAPI document
  AdapterConfig.java     Loads adapter.properties plus optional overrides
src/main/resources/adapter.properties   Default values and mapping tables
src/test/                Unit tests and staging-data fixtures
```

## Technology Stack

- Java 21, Maven
- Jackson (JSON processing)
- networknt json-schema-validator (validation against the OpenAPI schema)
- JUnit 5

## Configuration

Defaults are in [adapter.properties](src/main/resources/adapter.properties). Pass your own file with
`--config` to override any key.

| Key                              | Purpose                                                                                                                      |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `default.targetUsers`            | Comma-separated `targetUsers` for services (the source has no equivalent)                                                    |
| `default.languageAvailabilities` | Comma-separated language codes (the source has no equivalent)                                                                |
| `default.tagline`                | `name` copies the service name; any other value is used literally                                                            |
| `description.stripHtml`          | Strip HTML tags from the description (default `true`)                                                                        |
| `map.<field>.<value>`            | Value translation, e.g. `map.accessMode.access_type-virtual=`. Unlisted values pass through; an empty target drops the value |

### Mapping notes

- Required target fields with no source (`tagline`, `targetUsers`, `languageAvailabilities`) use the configured defaults.
- `accessTypes` (virtual/physical) is not the same concept as `accessModes` (free/paid) and is dropped unless mapped.
- `alternativeIdentifiers` comes only from `alternativePIDs`. `nodePID` identifies the node rather than the service, so it is not mapped.
- `helpdeskEmail` is the first email address in `publicContacts`.
- Optional fields without a value are omitted rather than set to `null`, which the target model does not allow.

## Contributing

Please read [CONTRIBUTING](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

See [Semantic Versioning](https://semver.org/) for guidance.

## Contributors

You can find the list of contributors in the [CONTRIBUTORS](CONTRIBUTORS.md) file.

## License

See the [LICENSE](LICENSE.txt) file.

## CITING

See the [CITATION](CITATION.cff) file.
