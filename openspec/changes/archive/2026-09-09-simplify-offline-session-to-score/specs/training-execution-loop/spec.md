## ADDED Requirements

### Requirement: 教师任务返回应到名单与成绩

系统 MUST 在教师场次任务详情中返回该计划全部参训人作为应到名单，并附带该场已评分成绩（无则为空）及对应内容投影
`resultStatus`。名单 MUST 来自计划参训人。计数 MUST 使用应到人数、已评分数、未评分数。权限仍为 `training:teacher:task` 且
MUST
校验本人场次教师归属。

#### Scenario: 教师打开场次看到应到人

- **GIVEN** 当前用户是该场次教师，计划有两名参训人且其中一人已评分
- **WHEN** 查询教师任务详情
- **THEN** 返回两名参训人
- **AND** 已评分者带分数，未评分者成绩为空
- **AND** `scoredCount` 为 1，`pendingScoreCount` 为 1

### Requirement: 线下场次每人至多一条成绩

系统 MUST 按 `offlineSessionId + trainingParticipantId` 保证至多一条有效成绩。再次提交 MUST 更新已有行，MUST NOT
插入第二条。投影 MUST 读取该参训人该场已评分成绩。

#### Scenario: 再次录分更新原行

- **GIVEN** 该参训人该场已有成绩
- **WHEN** 教师再次提交成绩（即使 `scoreName` 不同）
- **THEN** 系统更新已有行且不新增第二条
- **AND** 投影分数为最新提交值

## MODIFIED Requirements

### Requirement: 线下事实只能由合法教师处理

系统 MUST 支持教师在本人线下场次录入考核成绩。只有场次教师或明确授权的培训管理员可以处理。教师线下操作 MUST 同时满足
`training:teacher:task` 和本人场次归属。本期 MUST NOT 提供签到 HTTP。线下内容 MUST 以已评分成绩为完成事实。

#### Scenario: 教师处理本人场次

- **GIVEN** 当前教师被指派到目标线下场次
- **WHEN** 教师提交该参训人成绩
- **THEN** 系统保存登录评分人、评分时间和正式线下成绩

#### Scenario: 教师处理他人场次

- **GIVEN** 当前教师未被指派且没有培训管理权限
- **WHEN** 读取或修改目标场次
- **THEN** 系统返回 403 且不写入数据

### Requirement: 来源事实写入后自动刷新对应逐内容投影

系统 MUST 在培训绑定的课程学习状态变化、培训考试/题库开考或交卷或成绩发布、线下成绩保存之后，于同一事务按
`trainingParticipantId + planContentId` 幂等刷新唯一逐内容投影，并重建该参训人聚合字段。系统 MUST NOT
要求管理员先调用计划级结果同步。无培训参训人的独立开课或独立考试 MUST NOT 刷新培训投影。已冻结参训人投影 MUST NOT
被此次刷新覆盖。学员与结果查询 MUST 继续只读投影。计划级 `synchronize` MUST 仍可用于全量补账。线下签到 MUST NOT
再作为看见线下完成结果的必要写入。

#### Scenario: 学员交卷后内容项不再保持待开始

- **GIVEN** 培训考试或题库考生已生成 PENDING 投影
- **WHEN** 学员交卷且来源进入已提交或待发布
- **THEN** 对应投影 `resultStatus` 为 `IN_PROGRESS` 或 `PENDING_PUBLISH`

#### Scenario: 课程学习状态变化后投影跟随

- **GIVEN** 培训报名且投影为 PENDING
- **WHEN** 学习状态变为 COMPLETED
- **THEN** 对应投影为 `COMPLETED` 且 `onlineHours` 按课程学习秒数重建

#### Scenario: 教师保存成绩后投影跟随

- **GIVEN** 线下内容已有 PENDING 投影
- **WHEN** 场次教师保存该参训人已评分成绩
- **THEN** 系统在同一事务刷新该内容项投影
- **AND** 无须先确认签到

#### Scenario: 独立考试不刷新培训投影

- **GIVEN** 考生没有 `trainingParticipantId`
- **WHEN** 学员交卷或发布成绩
- **THEN** 系统不调用培训投影刷新

#### Scenario: 已冻结参训人忽略自动刷新

- **GIVEN** 参训人已填写 `resultFinalizedTime`
- **WHEN** 来源事实再次变化并触发刷新
- **THEN** 系统不覆盖投影行和最终快照

### Requirement: 安排学时与获得学时不得混用

系统 MUST 将计划/模板内容（及阶段）上的 `requiredHours` 视为安排学时，仅用于发布时校验类型与标准的总学时和线上占比。COURSE、EXAM、QUESTION_BANK
的安排学时计入线上，OFFLINE 计入线下。系统 MUST 将逐内容 `effectiveDurationSeconds` 与参训人 `onlineHours`、`offlineHours`
视为获得学时：COURSE 为正式学习秒数；OFFLINE 为该项 `COMPLETED` 时内容 `requiredHours` 换算的秒数，未评分或 `FAILED` MUST
为 0；EXAM/QUESTION_BANK MUST 为 0。线下获得学时 MUST NOT 再从出勤状态派生。

#### Scenario: 发布按安排学时校验

- **GIVEN** 计划含课程、考试、题库和线下，各填了 requiredHours
- **WHEN** 管理员发布
- **THEN** 系统按内容 requiredHours 合计总学时
- **AND** 不读取任何学员获得学时

#### Scenario: 考试完成后获得学时仍不含考试

- **GIVEN** 学员已完成培训考试且成绩已发布
- **WHEN** 查询参训结果
- **THEN** 该考试项 effectiveDurationSeconds 为 0

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

### Requirement: 学员可读取本人线下场次详情

系统 MUST 提供学员只读线下场次详情，权限 `training:learner:task`。请求 MUST NOT 携带 `userId`。系统 MUST
用当前登录用户在该场次所属计划上的参训记录定位本人；计划 MUST 为 RUNNING 或 ENDED。响应 MUST
包含名称、地点、起止以及本人已评分成绩与投影 `resultStatus` / `effectiveDurationSeconds`。MUST NOT
返回出勤或签到窗口。场次不存在、未参训或计划不可见时
MUST 返回 404 且业务码 8155。统一进入 OFFLINE MUST 仅返回跳转用 `offlineSessionId`。

#### Scenario: 参训学员读取本人场次

- **GIVEN** 当前用户是该场次计划的参训人，计划为 RUNNING，已评分
- **WHEN** 调用学员场次详情
- **THEN** 返回名称、地点、起止、本人成绩和投影学时/结果状态

#### Scenario: 未参训不可见

- **GIVEN** 场次存在但当前用户不是该计划参训人
- **WHEN** 调用学员场次详情
- **THEN** 系统返回 404/8155

#### Scenario: 结束后仍可查看

- **GIVEN** 本人参训且计划为 ENDED
- **WHEN** 调用学员场次详情
- **THEN** 系统返回该场次本人详情

## REMOVED Requirements

### Requirement: 确认缺勤或请假不得计入线下获得学时

**Reason**：出勤不再派生完成态与学时。

**Migration**：投影停止读取出勤；签到 HTTP 删除；表保留。
