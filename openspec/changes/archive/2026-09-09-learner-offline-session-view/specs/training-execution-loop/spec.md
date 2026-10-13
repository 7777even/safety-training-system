## ADDED Requirements

### Requirement: 学员可读取本人线下场次详情

系统 MUST 提供学员只读线下场次详情，权限 `training:learner:task`。请求 MUST NOT 携带 `userId`。系统 MUST
用当前登录用户在该场次所属计划上的参训记录定位本人；计划 MUST 为 RUNNING 或 ENDED。响应 MUST
包含场次通知字段（名称、地点、起止、签到窗口、出勤规则、场次状态）以及本人出勤、已评分成绩与投影 `resultStatus`/
`effectiveDurationSeconds`。MUST NOT 返回他人出勤名单或管理端编排字段（教师用户 ID、乐观锁版本）。场次不存在、当前用户未参训或计划对学员不可见时
MUST 返回 404 且业务码 8155。统一进入 OFFLINE MUST 仅返回跳转用 `offlineSessionId`；ENDED 计划 MUST 仍拒绝 enter，但本详情
GET MUST 可读。

#### Scenario: 参训学员读取本人场次

- **GIVEN** 当前用户是该场次计划的参训人，计划为 RUNNING，已有确认出勤和已评分
- **WHEN** 调用学员场次详情
- **THEN** 返回场次通知字段、本人出勤状态、成绩和投影学时/结果状态

#### Scenario: 未参训不可见

- **GIVEN** 场次存在但当前用户不是该计划参训人
- **WHEN** 调用学员场次详情
- **THEN** 系统返回 404/8155
- **AND** 不返回场次编排数据

#### Scenario: 结束后仍可查看

- **GIVEN** 本人参训且计划为 ENDED
- **WHEN** 调用学员场次详情
- **THEN** 系统返回该场次本人详情
