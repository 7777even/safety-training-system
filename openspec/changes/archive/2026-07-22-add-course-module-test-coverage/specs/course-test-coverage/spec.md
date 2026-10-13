## ADDED Requirements

### Requirement: Zero-DB tests cover course and asset services and controllers

The system SHALL maintain Mockito unit tests for course/asset service implementations that lack coverage, and MockMvc
web-slice tests for major controllers that lack coverage.

#### Scenario: Missing service tests added

- **WHEN** the test suite runs unit tests
- **THEN** `CourseDownloadAssetServiceImpl` and `CourseAssetReferenceServiceImpl` have dedicated zero-DB tests covering
  happy path and key error codes

#### Scenario: Missing web slices added

- **WHEN** the test suite runs unit tests
- **THEN** Publish/Offering/Enrollment/Learning/DownloadAsset/AssetReference/Asset controllers have web-slice tests
  verifying Result wrapping and at least one error path each where applicable

### Requirement: Integration tests cover unique keys and idempotent constraints

The system SHALL maintain Testcontainers MySQL tests for course/asset unique indexes and critical idempotent constraints
introduced by later course changes.

#### Scenario: Unique key ITs

- **WHEN** integration tests run with profile integration-test
- **THEN** Mapper ITs exist for course item live-session 1:1, course version (course_id,version_no), offering code,
  enrollment (offering,user), study session (enrollment,client_trace_id), download asset (course,asset), and asset code
  uniqueness with soft-delete reuse where applicable
