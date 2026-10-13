# 考试批阅任务与作答成绩分页

## ADDED Requirements

### Requirement: 教师本人批阅任务分页

系统 MUST 为当前登录教师提供批阅任务分页查询。任务 MUST 基于既有考试批阅人分配关系和主观题答案生成，且只返回当前教师被分配考试中的任务。

#### Scenario: 查询本人待批阅任务

- **GIVEN** 当前教师被分配为某考试的批阅人
- **AND** 该考试存在已提交且等待人工评分的主观题答案
- **WHEN** 教师查询待批阅任务
- **THEN** 系统分页返回该答案对应的批阅任务
- **AND** 返回考试、Candidate、Attempt、Answer、学员快照、提交时间、批阅状态和成绩发布状态

#### Scenario: 隔离未分配考试

- **GIVEN** 当前用户具有 `exam:review` 权限
- **BUT** 当前用户不是目标考试的批阅人
- **WHEN** 当前用户查询批阅任务
- **THEN** 系统不得返回目标考试的任何批阅任务

#### Scenario: 查询已批阅任务

- **GIVEN** 当前教师已对主观题答案完成追加式批阅
- **WHEN** 教师按 `REVIEWED` 状态查询
- **THEN** 系统返回该任务及最近一次批阅得分、意见和时间摘要
- **AND** 不改变或覆盖既有批阅记录

### Requirement: 考试作答成绩分页

系统 MUST 为考试管理人员提供作答记录分页，并按既有机构数据范围限制可见数据。列表 MUST 明确区分作答状态、批阅状态和成绩发布状态。

#### Scenario: 查询考试作答记录

- **GIVEN** 当前用户具有 `exam:attempt:list` 权限并可访问目标机构
- **WHEN** 当前用户按考试查询作答记录
- **THEN** 系统分页返回该考试可见范围内的 Attempt
- **AND** 每条记录包含 `examId`、`examCandidateId`、`examAttemptId`、必要学员信息、提交时间、批阅状态和成绩发布状态

#### Scenario: 筛选待批阅与未发布记录

- **GIVEN** 可见范围内同时存在不同批阅和发布状态的 Attempt
- **WHEN** 当前用户组合使用批阅状态和成绩发布状态筛选
- **THEN** 系统只返回同时满足筛选条件的记录
- **AND** 分页总数与过滤后的真实数据库记录数一致

#### Scenario: 隔离越权机构

- **GIVEN** 目标 Attempt 属于当前用户不可访问的机构
- **WHEN** 当前用户查询作答记录
- **THEN** 系统不得返回该 Attempt 或其学员、答案和成绩信息

### Requirement: 状态与人员字段稳定

系统 MUST 从既有业务事实派生批阅和成绩发布状态，并优先使用 Candidate 冻结人员快照提供必要学员展示信息。

#### Scenario: 安全处理不完整状态

- **GIVEN** Attempt 的人工题或完成时间事实不完整
- **WHEN** 系统派生批阅状态
- **THEN** 系统不得将其误报为 `COMPLETED`
- **AND** 成绩只有在 Attempt 为 `PUBLISHED` 且存在发布时间时才标记为 `PUBLISHED`

#### Scenario: 裁剪学员敏感字段

- **GIVEN** Candidate 人员快照包含超出列表展示所需的个人信息
- **WHEN** 系统返回批阅任务或作答记录
- **THEN** 仅返回用户ID、姓名、工号和所属机构等约定字段
- **AND** 不返回身份证号、手机号或原始人员快照 JSON

