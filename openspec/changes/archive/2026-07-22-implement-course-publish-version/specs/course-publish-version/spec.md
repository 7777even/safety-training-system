## ADDED Requirements

### Requirement: Publish course creates immutable version

The system SHALL create an immutable course version when an authorized user publishes a draft course, capturing a
complete content snapshot and checksum.

#### Scenario: First publish succeeds

- **WHEN** a user with `course:course:publish` publishes a valid course that has never been published
- **THEN** the system inserts `sts_course_version` with `version_no=1`, non-empty `content_snapshot_json` and
  `snapshot_hash`, sets course `current_version_id` to the new version, sets `publish_status=PUBLISHED`, and records
  `published_time` / `published_by`

#### Scenario: Republish with content changes creates next version

- **WHEN** the draft content differs from the current published snapshot and the user publishes again
- **THEN** the system inserts a new row with `version_no = previous + 1` and updates `current_version_id` to the new
  version
- **AND** previous version rows remain unchanged

#### Scenario: Identical content publish is idempotent

- **WHEN** the assembled snapshot hash equals the current version's `snapshot_hash`
- **THEN** the system MUST NOT insert a new version row
- **AND** MUST return the existing `courseVersionId`

### Requirement: Publish validates referential integrity

The system SHALL reject publish when the draft cannot be safely snapshotted.

#### Scenario: No chapters

- **WHEN** the course has zero chapters
- **THEN** publish fails with a business error (code 5051)

#### Scenario: ASSET item references unusable asset

- **WHEN** any ASSET item references a missing, cross-org, or non-SUCCEEDED asset
- **THEN** publish fails with a business error (code 5052 or 6011/6012)

#### Scenario: LIVE item missing session

- **WHEN** a LIVE item has no live session row
- **THEN** publish fails with business error 5052

### Requirement: Published versions are read-only

The system SHALL expose version list and detail APIs and MUST NOT provide update or delete APIs for
`sts_course_version`.

#### Scenario: List and get version

- **WHEN** a user with `course:course:list` requests versions for a course in their organization
- **THEN** the system returns version metadata (id, version_no, hash, note, published_by, published_time)
- **AND** detail includes `content_snapshot_json`

#### Scenario: Unknown version

- **WHEN** the version id does not belong to the course
- **THEN** the system returns business error 5053

### Requirement: Course can be taken offline without deleting versions

The system SHALL allow changing `publish_status` to `OFFLINE` while retaining all version rows and `current_version_id`.

#### Scenario: Offline after publish

- **WHEN** a user with `course:course:publish` offlines a PUBLISHED course
- **THEN** `publish_status` becomes `OFFLINE`
- **AND** historical versions remain queryable

### Requirement: Draft remains editable after publish

The system SHALL keep structured draft tables editable after publish; subsequent publish creates a new version when
content changes.

#### Scenario: Edit draft after publish

- **WHEN** an editor updates course/chapter/item draft fields after a successful publish
- **THEN** existing `sts_course_version` rows MUST remain byte-identical
- **AND** a later publish with changes creates a new version_no
