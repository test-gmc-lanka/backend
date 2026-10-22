# GMC Lanka – Backend

[![CI/CD Pipeline](https://github.com/test-gmc-lanka/backend/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/test-gmc-lanka/backend/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/test-gmc-lanka/backend/branch/develop/graph/badge.svg)](https://codecov.io/gh/test-gmc-lanka/backend)

REST API for the GMC Lanka system: products and categories, cart and checkout with Stripe card payments,
orders and invoices, appointments, custom designs, maintenance tasks for technicians, feedback,
complaints, notifications and the admin dashboard. Part of the
[test-gmc-lanka](https://github.com/test-gmc-lanka) project; the web client is in the
[frontend](https://github.com/test-gmc-lanka/frontend) repository, the integration tests in
[tests](https://github.com/test-gmc-lanka/tests) and the Dockerfile and CI/CD pipeline in
[infra](https://github.com/test-gmc-lanka/infra).

**Staging API:** _add the staging URL here once deployed_ · **API docs:** `<base-url>/swagger-ui.html`

## Tech stack

Java 21 · Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail) · MySQL 8 · JWT (jjwt) ·
Stripe · springdoc-openapi · JUnit 5 + Mockito + MockMvc · JaCoCo · Checkstyle + Spotless

## Prerequisites

- JDK 21 (no Maven install needed: use the included `./mvnw` wrapper)
- MySQL 8 with an empty database, e.g. `CREATE DATABASE gmc_db;`
- Docker (only to build or run the container image; see [infra](https://github.com/test-gmc-lanka/infra))

## Getting started

```bash
git clone https://github.com/test-gmc-lanka/backend.git
cd backend
git checkout develop
```

For `./mvnw verify`, also clone [tests](https://github.com/test-gmc-lanka/tests) next to this repository
(`<workspace>/backend` and `<workspace>/tests`); that is where the `*IT.java` suites live.

Configure the app. Every setting has a safe default in `src/main/resources/application.properties`;
you only need to supply your own values (see [.env.example](.env.example) for the full list).
Create `application-local.properties` next to `pom.xml` (gitignored) with at least:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/gmc_db
spring.datasource.username=root
spring.datasource.password=your-mysql-password
jwt.secret=output-of-openssl-rand-base64-48
```

Alternatively set the environment variables from `.env.example` (`DB_URL`, `DB_PASSWORD`, `JWT_SECRET`, …).

Run it:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

The API listens on http://localhost:8080 and Swagger UI is at http://localhost:8080/swagger-ui.html.
Tables are created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).

Without SMTP settings, emails (password-reset links, appointment updates) are printed to the console.
Without Stripe keys, checkout offers cash on delivery only.

## Commands

| Command                              | What it does                                                      |
| ------------------------------------ | ----------------------------------------------------------------- |
| `./mvnw spring-boot:run`             | Run the API locally                                               |
| `./mvnw test`                        | Unit tests (H2 in-memory DB) + coverage in `target/site/jacoco`   |
| `./mvnw verify`                      | Unit + integration tests (`../tests`), merged coverage, 80% gate |
| `./mvnw checkstyle:check`            | Lint                                                              |
| `./mvnw spotless:check`              | Formatting check                                                  |
| `./mvnw spotless:apply`              | Reformat the code (Palantir Java Format)                          |
| `./mvnw package -DskipTests`         | Build `target/backend-*.jar`                                      |

## Testing

- **Unit tests** (`*Test.java`) test one class with Mockito mocks.
- **Integration tests** (`*IT.java`) live in the [tests](https://github.com/test-gmc-lanka/tests)
  repository under `backend/src/test/java/com/gmc/backend/it/`. `pom.xml` adds that folder as a test
  source root (property `integration-tests.dir`, default `../tests/backend/src/test/java`). They call
  every REST endpoint through MockMvc with the real security filter chain and database: H2 locally,
  MySQL 8 in CI. Each test runs in a rolled-back transaction; email and Stripe are mocked.
- Coverage: `./mvnw verify` merges unit and integration coverage into `target/site/jacoco-merged`
  and fails below 80% line coverage.

## Running with Docker

The Dockerfile is in the [infra](https://github.com/test-gmc-lanka/infra) repository. With `infra`
cloned next to this repository:

```bash
docker build -f ../infra/docker/backend/Dockerfile -t gmc-backend .
docker run --rm -p 8080:8080 --env-file .env -v gmc-uploads:/app/uploads gmc-backend
```

`.env` is your copy of `.env.example`. Uploaded images are stored in `/app/uploads`; mount a
persistent volume there so they survive restarts and redeploys. To run the whole stack (MySQL, backend,
frontend), use `docker compose up --build` in `infra`.

## CI/CD

[.github/workflows/ci.yml](.github/workflows/ci.yml) sets the triggers and calls the pipeline defined in
[infra/.github/workflows/backend-ci.yml](https://github.com/test-gmc-lanka/infra/blob/main/.github/workflows/backend-ci.yml),
which runs:

| Stage               | Trigger                                      |
| ------------------- | -------------------------------------------- |
| Lint & Format       | Every push and PR                            |
| Build               | Every push and PR                            |
| Unit Tests          | Every push and PR (coverage report uploaded) |
| Integration Tests   | Push to `develop`, PR to `develop` (MySQL)   |
| Security Scan       | Push to `develop`/`main`, PRs (Trivy)        |
| Deploy – Staging    | Merge to `develop`                           |
| Deploy – Production | Merge to `main`, after manual approval       |

Deploys build a Docker image, push it to `ghcr.io/test-gmc-lanka/backend` and call the hosting
provider's deploy hook. A production deploy also creates the `vX.Y.Z` release tag from `pom.xml`.
Accepted scanner findings, each with a reason and an expiry date, are listed in
[infra/security/backend.trivyignore.yaml](https://github.com/test-gmc-lanka/infra/blob/main/security/backend.trivyignore.yaml).

## Branching and commits

- `main` – production, protected; `develop` – integration branch (default)
- `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `hotfix/<slug>`, `release/<version>`
- Conventional Commits, e.g. `feat(order): add invoice download` with `Closes #42` in the body
