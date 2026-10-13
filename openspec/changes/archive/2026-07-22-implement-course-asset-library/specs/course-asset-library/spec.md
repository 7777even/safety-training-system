## ADDED Requirements

### Requirement: Manage assets in organization library

The system SHALL allow administrators to create, update, list, and delete assets within the default organization, with
source-specific fields validated.

#### Scenario: Create UPLOAD asset

- **WHEN** an admin creates an asset with sourceType UPLOAD and a valid fileInfoId
- **THEN** an asset row is inserted and enters the processing state machine

#### Scenario: Create EXTERNAL asset

- **WHEN** an admin creates an asset with EXTERNAL and http(s) URL
- **THEN** processStatus becomes SUCCEEDED without a transcode job

### Requirement: Processing state gates course usage and edits

The system SHALL only allow SUCCEEDED assets to be referenced by courses, and SHALL restrict delete/edit while
PROCESSING.

#### Scenario: Processing not usable

- **WHEN** processStatus is PROCESSING
- **THEN** AssetReferenceService.requireUsableAsset fails with 6012

#### Scenario: Delete blocked when referenced

- **WHEN** course references exist for the asset
- **THEN** delete fails with 6023

### Requirement: Stub transcode jobs for audio and video

The system SHALL create transcode jobs for AUDIO/VIDEO and complete them via an in-process stub provider that marks the
asset SUCCEEDED (or FAILED on explicit failure simulation).

#### Scenario: Video asset completes via stub

- **WHEN** an AUDIO or VIDEO UPLOAD asset is created
- **THEN** a transcode job is recorded and the asset ends in SUCCEEDED under the stub provider
