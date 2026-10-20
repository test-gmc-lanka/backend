# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Integration tests (`*IT.java`) for every REST endpoint, run by Maven Failsafe against MySQL in CI.
- Merged unit + integration JaCoCo coverage with an enforced 80% line-coverage gate.
- Spotless (Palantir Java Format) formatting check alongside Checkstyle.
- Trivy dependency scan in CI, with documented exceptions in `.trivyignore.yaml`.
- Dockerfile, staging and production deploy jobs, and SemVer release tagging.
- Committed `application.properties` driven by environment variables, plus `.env.example`.
- Dependabot for Maven, GitHub Actions and Docker base images.

### Changed

- CI no longer ignores failing tests or coverage (`continue-on-error` removed).
- Upgraded Spring Boot 3.4.1 to 3.5.16 and springdoc-openapi 2.8.3 to 2.9.1.

### Security

- Pinned Tomcat 10.1.61 and Jackson 2.21.8 to fix high and critical CVEs, including a Tomcat
  remote code execution flaw (CVE-2025-24813) and Spring Security authorization bypasses.
- Client IPs are taken from `X-Forwarded-For` only when the request comes from a private-network
  proxy, so the login and sign-up rate limits cannot be bypassed by spoofing the header.
