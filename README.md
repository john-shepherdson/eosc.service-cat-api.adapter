# EOSC Beyond Service Catalogue API Adapter

<!-- markdownlint-disable MD013 -->

[![SQAaaS badge shields.io](https://img.shields.io/badge/sqaaas%20software-silver-lightgrey)](https://sqaaas.eosc-synergy.eu/full-assessment/report/https://raw.githubusercontent.com/eosc-synergy/eosc.service-cat-api.adapter.git.assess.sqaaas/main/.report/assessment_output.json "SQAaaS silver badge achieved")

<!-- markdownlint-enable MD013 -->

This repository contains the source code for a Java adapter that converts
service descriptions from the
[EOSC Beyond Service Catalogue API](https://service-catalogue-staging.beyond.cessda.eu/api/service/all)
into JSON that complies with the EOSC service catalogue model defined in
[EOSC-Lot-1/eosc-service-catalogues-specs](https://github.com/EOSC-Lot-1/eosc-service-catalogues-specs).

Each converted service is validated against the `ServiceBundle` schema of the
selected model release. Services that fail validation are excluded from the
output and reported with the reasons.

## Target model

The adapter supports these releases of the EOSC-Lot-1 specification. The
OpenAPI document of each release is bundled in the jar
(`src/main/resources/schemas`), so no network access is needed to validate.

- `v2.0.0`: new `service_classification-*` service categories. This is the
  latest release and the default.
- `v1.0.0`: the original release, which uses the same `category-*` vocabulary
  as EOSC Beyond.

Select a release with `--model-version`. Both releases differ only in the
category vocabulary.

## Prerequisites

- Java 21
- Maven 3.9+

## Quick Start

1. Check prerequisites and install any required software.
1. Clone the repository to your local workspace.
1. Build the executable jar (this also runs the tests):

   ```bash
   mvn clean package
   ```

   Use `clean`: building again without it shades the jar from the previous
   build, which floods the log with overlap warnings.

1. Run the adapter:

   ```bash
   java -jar target/eosc-adapter-x.x.x.jar \
     --source
      https://service-catalogue-staging.beyond.cessda.eu/api/service/all \
     --output services.json
   ```

### Command line options

- `--source <location>`: EOSC Beyond service list, as a URL or a file path
  (required).
- `--model-version <version>`: EOSC-Lot-1 model release to produce: `v1.0.0`,
  `v2.0.0` or `latest` (default: `latest`, currently `v2.0.0`).
- `--config <file>`: properties file overriding the built-in defaults.
- `--output <file>`: output file name (default: `services.json`). A relative
  name goes in the outputs directory; an absolute path is used as is.
- `--outdir <dir>`: outputs directory (default: `outputs`).
- `--strict`: exit with status 2 if any service was rejected.

The result is written to `outputs/services.json` by default. The contents of
`outputs/` are excluded from commits by `.gitignore`. A summary and any
rejected services are written to stderr.

1. Run the adapter using a different model and different output file name:

   ```bash
   java -jar target/eosc-adapter-x.x.x.jar \
     --source
      https://service-catalogue-staging.beyond.cessda.eu/api/service/all \
     --output v1-CESSDA-services.json --model-version v1.0.0
   ```

## Project Structure

```text
pom.xml
src/main/java/eu/cessda/eosc/adapter/
  Main.java             Command line entry point
  Adapter.java          Converts a whole response, validating each service
  ServiceMapper.java    Maps one EOSC Beyond service to a Federation bundle
  ModelVersion.java     Supported EOSC-Lot-1 releases and their bundled schemas
  BundleValidator.java  JSON Schema validation against the target OpenAPI
  AdapterConfig.java    Loads adapter.properties plus optional overrides
src/main/resources/
  adapter.properties    Default values and mapping tables
  schemas/              Bundled EOSC-Lot-1 OpenAPI documents, one per release
src/test/               Unit tests and staging-data fixtures
```

## Technology Stack

- Java 21, Maven
- Jackson (JSON processing)
- networknt json-schema-validator (validation against the OpenAPI schema)
- JUnit 5

## Configuration

Defaults are in
[adapter.properties](src/main/resources/adapter.properties). Pass your own file
with `--config` to override any key.

- `default.targetUsers`: comma-separated `targetUsers` for services (the source
  has no equivalent).
- `default.languageAvailabilities`: comma-separated language codes (the source
  has no equivalent).
- `default.tagline`: `name` copies the service name; any other value is used
  literally.
- `description.stripHtml`: strip HTML tags from the description (default
  `true`).
- `map.<field>.<value>`: value translation, for example
  `map.accessMode.access_type-virtual=`. Unlisted values pass through; an empty
  target drops the value.
- `map.category.v2.<value>`: default translation of EOSC Beyond categories to
  the `service_classification-*` values of model `v2.0.0`.
- `map.subcategory.v2.<value>`: translation of an EOSC Beyond subcategory,
  which takes precedence over the category default.

### Mapping notes

- Required target fields with no source (`tagline`, `targetUsers`,
  `languageAvailabilities`) use the configured defaults.
- `accessTypes` (virtual/physical) is not the same concept as `accessModes`
  (free/paid) and is dropped unless mapped.
- `alternativeIdentifiers` comes only from `alternativePIDs`. `nodePID`
  identifies the node rather than the service, so it is not mapped.
- `helpdeskEmail` is the first email address in `publicContacts`.
- Optional fields without a value are omitted rather than set to `null`.
- The EOSC-Lot-1 model has no subdomain or subcategory, so the EOSC Beyond
  `scientificSubdomain` and `subcategory` values are not carried over.
- For `v2.0.0`, categories are translated in two steps. The EOSC Beyond
  subcategory decides first (`map.subcategory.v2.*`, which lists only the
  subcategories that differ from their category). Otherwise the category
  default applies (`map.category.v2.*`). The assignments are a judgement based
  on the category descriptions in the model, so review them. Several EOSC
  Beyond values can map to one new category; duplicates are removed.
- A category or scientific domain that the selected model does not allow causes
  the service to be rejected.
- `languageAvailabilities` defaults to `en`. The model accepts any string, and
  its own examples use upper case (`EN`), so adjust the default if consumers
  expect that.

## Known limitations

- The bundled models are snapshots of the tagged releases. When EOSC-Lot-1
  publishes a new release, add its OpenAPI document to
  `src/main/resources/schemas` and a constant to `ModelVersion`.
- The models are lenient (they do not forbid extra fields or empty lists), so
  passing validation does not prove that a record is complete. A service with
  no categories is valid, for example.
- `accessTypes` (virtual/physical) has no equivalent in `accessModes` and is
  dropped unless mapped.
- The shape of non-null `alternativePIDs` is untested, as every service in the
  staging data has `null`.
- Paging is not implemented. The EOSC Beyond API returns all services in one
  response, and the result is written as a single file.

## Contributing

Please read [CONTRIBUTING](CONTRIBUTING.md) for details on our code of
conduct, and the process for submitting pull requests to us.

## Versioning

See [Semantic Versioning](https://semver.org/) for guidance.

## Contributors

You can find the list of contributors in the
[CONTRIBUTORS](CONTRIBUTORS.md) file.

## License

See the [LICENSE](LICENSE.txt) file.

## CITING

See the [CITATION](CITATION.cff) file.
