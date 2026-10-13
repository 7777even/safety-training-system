## ADDED Requirements

### Requirement: 确认缺勤或请假不得计入线下获得学时

系统 MUST 按已确认出勤状态计算线下获得学时与完成态。仅 `PRESENT` 与 `LATE` 可计入该项 `effectiveDurationSeconds` 及参训人
`offlineHours`；`SIGN_IN_OUT` 场次仍须已签到且签退。已确认 `ABSENT` 或 `LEAVE` 的 `effectiveDurationSeconds` MUST 为 0，
`resultStatus` MUST 为 `FAILED`，MUST NOT 为 `COMPLETED`；成绩 MUST NOT 把缺勤或请假改成完成。确认仅使出勤记录作数，学时
MUST 由服务端从出勤状态派生。迟到 MUST NOT 按折扣折算。

#### Scenario: 确认缺勤后获得学时为 0

- **GIVEN** 线下场次已记录并确认 `ABSENT`
- **WHEN** 系统刷新该参训人该内容投影
- **THEN** 该项 `effectiveDurationSeconds` 为 0
- **AND** `offlineHours` 不含该场次时长
- **AND** `resultStatus` 为 `FAILED`

#### Scenario: 确认请假后不得完成

- **GIVEN** 线下场次已记录并确认 `LEAVE`
- **WHEN** 系统刷新该参训人该内容投影
- **THEN** 该项 `effectiveDurationSeconds` 为 0
- **AND** `resultStatus` 为 `FAILED` 且不得为 `COMPLETED`

#### Scenario: 确认正常出勤仍计有效学时

- **GIVEN** 线下场次规则为签到，已确认 `PRESENT`，且无及格线
- **WHEN** 系统刷新该参训人该内容投影
- **THEN** 该项 `effectiveDurationSeconds` 为场次时长
- **AND** 该秒数计入 `offlineHours`
