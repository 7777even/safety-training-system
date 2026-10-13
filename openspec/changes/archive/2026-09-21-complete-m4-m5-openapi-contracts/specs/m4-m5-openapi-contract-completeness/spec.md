# M4/M5 OpenAPI Contract Completeness

## ADDED Requirements

### Requirement: Current public APIs SHALL have complete machine-readable contracts

Every public M4/M5 Controller operation in scope SHALL have an OpenAPI operation with the same URL and method, the real
Controller tag, accurate authentication or permission metadata, concrete request and response schemas, examples, and
applicable error responses.

#### Scenario: A Controller operation already exists

- **WHEN** a caller reads the corresponding OpenAPI document
- **THEN** the existing operation is present and fully typed
- **AND** documenting it does not change runtime behavior.

### Requirement: Result data SHALL use concrete schemas

Every JSON success response SHALL give its `data` member a concrete machine-readable business type.

#### Scenario: An endpoint returns business data

- **WHEN** the success response is described
- **THEN** its data field references the concrete DTO, VO, list, page, scalar, ID, void, or binary schema
- **AND** an example does not replace the schema definition.

### Requirement: Removed public capabilities SHALL leave no unreachable contract components

OpenAPI documents SHALL remove components that are reachable only from public capabilities already removed by confirmed
specifications.

#### Scenario: Minimal-scope refactoring removed an API

- **WHEN** the affected OpenAPI document is inspected
- **THEN** the removed path and components used only by that path are absent
- **AND** components still used by current operations remain.

### Requirement: Authenticated-only operations SHALL declare authentication metadata

Authenticated-only operations SHALL declare their login requirement without inventing a feature permission code.

#### Scenario: Identity is derived from login state without a feature permission annotation

- **WHEN** the operation is documented
- **THEN** Bearer security and `x-permission: authenticated` are declared
- **AND** conditional visibility rules are described without inventing a permission code.

### Requirement: Hidden certificate fields SHALL NOT remain in public models

Certificate public request and response models SHALL omit fields already hidden by the confirmed M4 minimum-scope
specification while preserving database compatibility columns and historical values.

#### Scenario: A certificate issue rule is created

- **WHEN** a caller uses the public create request
- **THEN** `conditionValue` and `collectionInstruction` are not declared or written.

#### Scenario: A certificate is returned

- **WHEN** an administrator or learner queries issued certificates
- **THEN** `revokedTime` is not present in the public response model.
