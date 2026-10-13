## ADDED Requirements

### Requirement: Manage course offerings bound to published versions

The system SHALL allow administrators to create, update, publish, end, and cancel course offerings. Publishing MUST
freeze `course_version_id` to the course's then-current published version.

#### Scenario: Publish offering freezes version

- **WHEN** an admin publishes a DRAFT offering for a PUBLISHED course with non-null `current_version_id`
- **THEN** the offering `course_version_id` is set to that version and MUST NOT change afterward
- **AND** offering `publish_status` becomes PUBLISHED

#### Scenario: Cannot publish offering without course version

- **WHEN** the course is not PUBLISHED or has no `current_version_id`
- **THEN** offering publish fails with a business error

### Requirement: Enrollment uniqueness and elective withdraw

The system SHALL enforce one active enrollment per user per offering and apply elective self-withdraw rules.

#### Scenario: Assign enrollment

- **WHEN** an admin assigns users to an offering
- **THEN** enrollments are created with source ASSIGN and learning_status NOT_STARTED
- **AND** duplicate user on same offering is rejected or ignored idempotently

#### Scenario: Self enroll on OPEN offering

- **WHEN** a logged-in user self-enrolls on a PUBLISHED offering with self-enroll allowed
- **THEN** an enrollment with source SELF is created

#### Scenario: Elective withdraw

- **WHEN** an ELECTIVE enrollee who is NOT COMPLETED requests withdraw
- **THEN** the enrollment is logically deleted
- **AND** REQUIRED offerings reject self-withdraw

### Requirement: Idempotent study sessions update progress

The system SHALL accept study session reports keyed by `client_trace_id` and update item progress and enrollment
aggregates.

#### Scenario: First session report

- **WHEN** a learner submits a new `client_trace_id` session for an enrolled item
- **THEN** a study session row is inserted and item progress / enrollment totals are updated

#### Scenario: Duplicate trace id

- **WHEN** the same enrollment and `client_trace_id` is submitted again
- **THEN** no second session is inserted and study seconds are not double-counted

#### Scenario: Completion rule ALL_REQUIRED_ITEMS

- **WHEN** all required items in the frozen version snapshot are COMPLETED
- **THEN** enrollment `learning_status` becomes COMPLETED and `completed_time` is set

### Requirement: Course evaluation per enrollment

The system SHALL allow at most one evaluation per enrollment after completion.

#### Scenario: Submit evaluation

- **WHEN** a COMPLETED enrollee submits a rating
- **THEN** an evaluation row is stored
- **AND** a second submit for the same enrollment fails

### Requirement: Block content-mode switch when offerings exist

The system SHALL reject course content-mode switches when the course has offerings in DRAFT or PUBLISHED status.

#### Scenario: Switch blocked

- **WHEN** an editor switches content-mode while a PUBLISHED offering references the course
- **THEN** the operation fails with a business error
