# Certificate Management Workflow

## ADDED Requirements

### Requirement: Template design SHALL use a replaceable draft and immutable published versions

The system SHALL keep at most one replaceable DRAFT for each certificate template. PUBLISHED and ARCHIVED versions and
their elements SHALL remain immutable.

#### Scenario: Save a design for the first time

- **GIVEN** the tenant template has no draft
- **WHEN** an administrator saves the background, canvas, text or variable elements, coordinates, and font styles
- **THEN** the system creates a DRAFT with version number `currentVersionNo + 1`
- **AND** the template detail returns the complete draft and its elements
- **AND** no PUBLISHED or ARCHIVED version is modified.

#### Scenario: Save an existing draft again

- **GIVEN** the template already has a DRAFT
- **WHEN** the administrator saves the design again
- **THEN** the system replaces the design within the same DRAFT version
- **AND** an optimistic-lock mismatch returns HTTP 409 without silently overwriting data.

#### Scenario: Publish a template draft

- **GIVEN** the template has a valid background and element design
- **WHEN** the administrator publishes the template
- **THEN** the system atomically promotes the DRAFT to PUBLISHED
- **AND** the previous current PUBLISHED version becomes ARCHIVED
- **AND** `currentVersionNo` points to the new published version
- **AND** historical versions referenced by issued certificates remain unchanged.

### Requirement: Template detail SHALL support reliable design readback

Template detail SHALL return template metadata together with the current published version and current draft, including
complete ordered elements.

#### Scenario: Read a template with both published and draft designs

- **GIVEN** a tenant template has a current published version and a next-version draft
- **WHEN** the administrator reads the template detail
- **THEN** the response contains template metadata, both versions, their backgrounds, canvases, statuses, and ordered
  elements
- **AND** no template, version, or element from another tenant is returned.

### Requirement: Certificate PNG output SHALL use the published background and styles

Preview and issuance SHALL render the real template background and apply the saved TEXT and VARIABLE element styles.

#### Scenario: Preview or issue a certificate

- **GIVEN** a published template has a readable image background and TEXT or VARIABLE elements
- **WHEN** the system previews the template or issues a certificate to an eligible learner
- **THEN** the PNG uses the real background image
- **AND** static text and resolved variables use the saved coordinates, font family, font size, and color
- **AND** preview and issuance use the same rendering semantics.

#### Scenario: The background file is unavailable

- **GIVEN** the background file is missing or cannot be decoded as an image
- **WHEN** the administrator saves or publishes the template design
- **THEN** the request is rejected
- **AND** the system does not substitute a placeholder background and report success.

### Requirement: A template referenced by an issue rule SHALL NOT be deleted

The system SHALL protect an active certificate template from deletion while an undeleted issue rule references it.

#### Scenario: Delete a referenced template

- **GIVEN** an undeleted issue rule in the current tenant references the template
- **WHEN** an administrator deletes the template
- **THEN** the system returns HTTP 409
- **AND** the template, versions, and elements remain available.

### Requirement: Certificate issue rules SHALL support detail and controlled updates

The system SHALL provide tenant-scoped rule detail and update operations. Rule updates SHALL validate template and
source eligibility and use optimistic locking.

#### Scenario: Read rule detail

- **GIVEN** a certificate issue rule exists in the current tenant
- **WHEN** an administrator reads it by ID
- **THEN** the response contains the complete configuration, optimistic-lock version, template name, and source name
- **AND** a rule belonging to another tenant is treated as not found.

#### Scenario: Update a rule

- **GIVEN** the administrator has `certificate:rule:edit` and submits the current version
- **WHEN** the template, source, automatic-issue flag, or enabled status is updated
- **THEN** the system validates that the template and source are bindable before updating
- **AND** duplicate configuration or an optimistic-lock conflict returns HTTP 409
- **AND** previously issued certificates are not modified.

### Requirement: Rule sources SHALL be selected through a unified candidate endpoint

The system SHALL expose one paged candidate endpoint for COURSE, EXAM, and TRAINING sources. The endpoint and rule
writes SHALL enforce tenant and publication eligibility on the server.

#### Scenario: Query bindable sources

- **GIVEN** an administrator selects `COURSE`, `EXAM`, or `TRAINING`
- **WHEN** the administrator pages through rule source candidates
- **THEN** the system returns only eligible sources in the current tenant
- **AND** every item has a source type, ID, code, name, and publication status
- **AND** draft, cancelled, archived, or ended sources that cannot receive a new binding are absent.

#### Scenario: Bypass the candidate endpoint with an invalid source

- **GIVEN** a client submits a source ID from another tenant or a source that is missing, unpublished, or ended
- **WHEN** the client creates or updates an issue rule
- **THEN** the server rejects the request
- **AND** correctness does not depend on the client candidate list.

## MODIFIED Requirements

### Requirement: Certificates SHALL automatically produce real files from eligible source results

Certificate template management SHALL support background and TEXT or VARIABLE element draft saving, detail readback,
immutable publishing, and protected deletion. COURSE, EXAM, and TRAINING issue rules SHALL support create, detail,
update, delete, and tenant-scoped eligible source selection. Eligible learner results SHALL continue to trigger
automatic idempotent PNG issuance, and the PNG SHALL use the real published background and styles.

#### Scenario: A learner first reaches an eligible result

- **GIVEN** an enabled issue rule references an enabled template with a published version
- **WHEN** a learner first reaches the eligible course, theory-exam, or training result
- **THEN** the system issues one idempotent certificate
- **AND** the generated PNG uses the frozen published template version and content snapshot.

## REMOVED Requirements

None. This change does not restore revocation, manual reissue, arbitrary client-triggered issuance, expiration
management, or public version-history APIs.
