## ADDED Requirements

### Requirement: Learner progress visibility for admins

The system SHALL provide admin read APIs for enrollment lists and per-enrollment learning detail (item progresses and
recent sessions summary).

#### Scenario: View learning detail

- **WHEN** an admin with `course:learning:view` requests enrollment detail
- **THEN** the response includes learning_status, progress_percent, total_study_seconds, item progresses, and recent
  sessions
