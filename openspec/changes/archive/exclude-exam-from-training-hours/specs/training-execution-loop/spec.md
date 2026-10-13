## ADDED Requirements

### Requirement: 考试与题库不得填写安排学时

系统 MUST 在保存培训模板或培训计划阶段内容时，将 `EXAM` 与 `QUESTION_BANK` 的 `requiredHours` 视为不可填写的学时：请求省略、
`null` 或 `0` MUST 落库为 `0`；`requiredHours > 0` MUST 拒绝保存，HTTP 400、业务码 `8165`，且 MUST NOT 写库。从模板复制到计划草稿时，系统
MUST 将这两类内容的 `requiredHours` 置为 `0`。响应中这两类 `requiredHours` MUST 为 `0`。

#### Scenario: 考试不填学时可以保存

- **GIVEN** 草稿计划阶段含 EXAM 内容且未提交 requiredHours
- **WHEN** 管理员保存阶段内容
- **THEN** 该考试内容 requiredHours 落库为 0
- **AND** 保存成功

#### Scenario: 考试填写正数学时被拒绝

- **GIVEN** 草稿计划阶段含 EXAM 内容
- **WHEN** 管理员提交 requiredHours 为 2
- **THEN** 系统返回 400/8165
- **AND** 不写库

#### Scenario: 从旧模板复制考试学时归零

- **GIVEN** 已发布模板中某 EXAM 内容 requiredHours 为 2
- **WHEN** 计划草稿引用该模板复制课表
- **THEN** 计划中该考试内容 requiredHours 为 0

## MODIFIED Requirements

### Requirement: 安排学时与获得学时不得混用

系统 MUST 将计划/模板中 COURSE 与 OFFLINE 内容上的 `requiredHours` 视为安排学时，仅用于发布时校验类型与标准的总学时和线上占比。总学时
MUST 只合计 COURSE 与 OFFLINE 的 `requiredHours`。COURSE 的安排学时计入线上，OFFLINE 计入线下。EXAM 与 QUESTION_BANK MUST
NOT 计入总学时或线上占比，其 `requiredHours` MUST 为 0。系统 MUST 将逐内容 `effectiveDurationSeconds` 与参训人
`onlineHours`、`offlineHours` 视为获得学时：COURSE 为正式学习秒数；OFFLINE 为该项 `COMPLETED` 时内容 `requiredHours`
换算的秒数，未评分或 `FAILED` MUST 为 0；EXAM/QUESTION_BANK MUST 为 0 且 MUST NOT 计入 `onlineHours`。QUESTION_BANK 执行配置
`durationSeconds` MUST 仅表示作答时限，MUST NOT 作为培训学时。计划结束 MUST NOT 用个人获得学时重算 TOTAL_HOURS
或线上占比。线下获得学时 MUST NOT 再从出勤状态派生。监管计划总学时与线上占比检测 MUST 使用与发布相同的安排学时合计。

#### Scenario: 发布按课程与线下安排学时校验

- **GIVEN** 计划含课程 requiredHours 6、考试 requiredHours 0、题库 requiredHours 0、线下 requiredHours 16，类型最低学时
  24、线上上限 30%
- **WHEN** 管理员发布
- **THEN** 系统按课程与线下合计总学时 22，线上占比为 6/22
- **AND** 因总学时低于 24 以 8121 拒绝发布
- **AND** 不把考试计入总学时或线上占比
- **AND** 不读取任何学员获得学时

#### Scenario: 仅课程计入线上占比

- **GIVEN** 计划含课程 requiredHours 6、考试 requiredHours 0、线下 requiredHours 18，线上上限 30%
- **WHEN** 管理员发布预检
- **THEN** 线上占比按 6/24 计算
- **AND** 考试不抬高线上占比

#### Scenario: 考试完成后获得学时仍不含考试

- **GIVEN** 学员已完成培训考试且成绩已发布
- **WHEN** 查询参训结果
- **THEN** 该考试项 effectiveDurationSeconds 为 0
- **AND** onlineHours 仅含课程正式学习秒换算的小时

#### Scenario: 线下及格后获得安排学时

- **GIVEN** 线下内容 requiredHours 为 2 且无及格线
- **WHEN** 教师保存该参训人成绩
- **THEN** 该项 resultStatus 为 COMPLETED
- **AND** effectiveDurationSeconds 为 7200

#### Scenario: 线下不及格不计获得学时

- **GIVEN** 线下内容配置了及格线且教师保存的分数低于及格线
- **WHEN** 系统刷新该内容投影
- **THEN** 该项 resultStatus 为 FAILED
- **AND** effectiveDurationSeconds 为 0
