# training-execution-loop Specification

## Purpose

培训计划从发布到结束形成真实执行闭环：冻结可执行资源、生成四类计划专属执行、学员从培训上下文进入、按正式事实投影结果，并在
RUNNING 结束时冻结带 hash 的最终快照。机器可读契约见 `docs/api/training.openapi.json`。
## Requirements
### Requirement: 培训计划冻结可执行资源

计划发布时 MUST 冻结类型、标准、模板结构、评价表单版本、课程版本、试卷版本、题库引用和线下执行规则。已发布计划结构和参训名单
MUST 只读。

#### Scenario: 发布混合培训计划

- **GIVEN** 草稿计划包含有效参训人及 COURSE、EXAM、QUESTION_BANK、OFFLINE 内容
- **WHEN** 管理员携带当前版本发布计划
- **THEN** 系统校验机构、发布状态和执行配置，冻结全部资源与结构快照并推进为 PUBLISHED

#### Scenario: 来源资源发布新版本

- **GIVEN** 培训计划已经发布
- **WHEN** 来源课程或考试产生新版本
- **THEN** 培训计划继续使用冻结版本且历史结构不变化

### Requirement: 四类培训内容均可真实执行

系统 MUST 支持 COURSE、EXAM、QUESTION_BANK 和 OFFLINE。EXAM MUST 区分 QUIZ 和 FORMAL_EXAM；QUESTION_BANK MUST
形成题库练习，不得替换为专项练习。最小版本的 QUESTION_BANK MUST 只包含可自动判分题型。EXAM 发布快照 MUST
自足并版本化，包含完整考试策略和批阅人；计划专属执行生成 MUST NOT 依赖来源 Exam、Policy、Execution 或 Reviewer
的当前状态。包含非自动判分题目的计划专属考试 MUST 创建计划专属 Reviewer。QUESTION_BANK 首次 Attempt MUST
按冻结抽题规则生成可复现的题目、顺序、分值和随机种子快照；TRAINING_PRACTICE 缺少有效冻结抽题规则时 MUST 拒绝开考，不得回退到课程题库全量题目。

#### Scenario: 生成真实执行

- **GIVEN** 已发布计划包含四类内容
- **WHEN** 管理员生成执行
- **THEN** 系统创建真实 CourseOffering/Enrollment、计划专属 Exam/AssessmentExecution/Candidate、BANK_PRACTICE
  Execution/Candidate 和 OfflineSession

#### Scenario: 题库练习缺少配置

- **GIVEN** QUESTION_BANK 内容缺少时长、及格线、次数或抽题规则
- **WHEN** 管理员发布计划
- **THEN** 系统拒绝发布且不猜测业务默认值

#### Scenario: 计划专属考试使用发布快照

- **GIVEN** 来源考试的当前策略或批阅人已变化
- **WHEN** 系统为已发布计划生成 EXAM 执行
- **THEN** 计划专属 Exam 与 AssessmentExecution 使用发布时冻结的资源、策略和批阅人快照
- **AND** 不读取来源考试的当前状态

#### Scenario: 题库练习按冻结规则开考

- **GIVEN** QUESTION_BANK 内容已冻结抽题规则
- **WHEN** 学员首次进入并开考
- **THEN** 系统按冻结规则生成可复现的题目、顺序、分值和随机种子快照
- **AND** Candidate 由同一冻结计分规则形成正式结论

#### Scenario: 题库练习缺少冻结抽题规则

- **GIVEN** TRAINING_PRACTICE 没有有效冻结抽题规则
- **WHEN** 学员开考
- **THEN** 系统拒绝开考
- **AND** 不使用课程题库的全量题目兼容分支

### Requirement: 培训执行幂等且上下文隔离

每个 planContent MUST 只有一个真实执行；重复生成不得重复创建实例。EXAM/QUESTION_BANK MUST 使用计划专属
AssessmentExecution，不能共享来源考试 Candidate，也不能用资源 ID 冒充 executionId。COURSE 的 executionId MUST 指向
CourseOffering，EXAM/QUESTION_BANK MUST 指向 AssessmentExecution，OFFLINE MUST 指向 OfflineSession。generate-executions
MUST 按 planContentId 创建或复用计划专属实例，不得关联来源资源的既有执行。

#### Scenario: 同一考试用于两个计划

- **GIVEN** 两个计划引用相同来源考试且包含同一用户
- **WHEN** 分别生成执行
- **THEN** 系统创建两个计划专属 AssessmentExecution 和两个 Candidate，作答与成绩互不影响

#### Scenario: 单内容重试

- **GIVEN** 某内容首次生成失败而其他内容成功
- **WHEN** 修复原因后重试该内容
- **THEN** 系统保留其他成功执行并通过 planContentId 创建或找回唯一实例

### Requirement: 学员从培训上下文安全进入

系统 MUST 为当前登录学员提供本人任务、详情和结果，并从 participantId 与 contentId 派生课程报名或考试 Attempt。请求 MUST NOT
接受 userId、examId、assessmentExecutionId 或 contextType。

#### Scenario: 学员进入本人任务

- **GIVEN** 当前用户是计划参训人且内容执行已生成
- **WHEN** 学员进入课程、考试或题库练习
- **THEN** 系统返回本人的 CourseEnrollment 或创建/恢复培训上下文 Attempt

#### Scenario: 访问他人任务

- **GIVEN** participantId 不属于当前登录用户
- **WHEN** 用户查询或进入内容
- **THEN** 系统返回 403 且不泄露他人资料和成绩

### Requirement: 线下场次状态受控推进

系统 MUST 将线下场次 `sessionStatus` 限制为 `DRAFT`、`SCHEDULED`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`。手工创建 MUST
写入 `DRAFT`。计划生成执行 MUST 写入 `SCHEDULED`。普通更新 MUST NOT 修改 `sessionStatus`。状态推进 MUST 仅通过
`start`（`SCHEDULED` → `IN_PROGRESS`）、`complete`（`IN_PROGRESS` → `COMPLETED`）和
`cancel`（`SCHEDULED` 或 `IN_PROGRESS` → `CANCELLED`）。非法转移 MUST 返回 409 / `8202`。版本冲突 MUST 返回 409 /
`8204`。无计划编辑权限时 MUST 校验本场教师，否则 403 / `8131`。

#### Scenario: 开始已安排场次

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 本场教师或培训管理员调用 start 并回传当前版本
- **THEN** 状态变为 `IN_PROGRESS`

#### Scenario: 更新场次不改状态

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 调用普通更新
- **THEN** 名称地点等可改且 `sessionStatus` 仍为 `SCHEDULED`

### Requirement: 仅进行中场次可录分

系统 MUST 仅在场次 `IN_PROGRESS` 时接受线下成绩写入。其它状态 MUST 返回 409 / `8203`。教师任务 `availableActions` MUST
按状态给出 `START`/`CANCEL`（已安排）、`RECORD_SCORE`/`COMPLETE`/`CANCEL`（进行中），其它状态为空。教师任务列表和详情 MUST
返回场次乐观锁 `version`，供 `start`/`complete`/`cancel` 回传。

#### Scenario: 未开始不可录分

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 教师提交成绩
- **THEN** 返回 409 / `8203` 且不写成绩、不刷新投影

#### Scenario: 教师任务返回场次版本

- **GIVEN** 当前用户是该场次教师，场次乐观锁版本为 4
- **WHEN** 查询教师任务列表或详情
- **THEN** 返回 `version` 为 4

### Requirement: 线下事实只能由合法教师处理

系统 MUST 支持教师在本人线下场次录入考核成绩。只有场次教师或明确授权的培训管理员可以处理。教师线下操作 MUST 同时满足
`training:teacher:task` 和本人场次归属。系统 MUST NOT 提供签到 HTTP、签到配置或签到记录存储，线下内容 MUST 以已评分成绩为完成事实。仅
`IN_PROGRESS` 场次可录分。

#### Scenario: 教师处理本人场次

- **GIVEN** 当前教师被指派到目标线下场次
- **WHEN** 教师提交该参训人成绩
- **THEN** 系统保存登录评分人、评分时间和正式线下成绩
- **AND** 无需创建、确认或查询签到记录

#### Scenario: 教师处理他人场次

- **GIVEN** 当前教师未被指派且没有培训管理权限
- **WHEN** 读取或修改目标场次
- **THEN** 系统返回 403 且不写入数据

### Requirement: 教师任务返回应到名单与成绩

系统 MUST 在教师场次任务详情中返回该计划全部参训人作为应到名单，并附带该场已评分成绩（无则为空）及对应内容投影
`resultStatus`。名单 MUST 来自计划参训人。计数 MUST 使用应到人数、已评分数、未评分数。列表与详情 MUST 返回场次乐观锁
`version`。权限仍为 `training:teacher:task` 且 MUST 校验本人场次教师归属。

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

### Requirement: 培训结果由正式来源同步到逐内容投影

系统 MUST 按 trainingParticipantId + planContentId 读取课程、考试和线下正式事实，并幂等更新唯一的逐内容结果投影。投影 MUST
保存 executionId、来源记录、学时、分数，并分别保存来源域 `sourceStatus` 和培训域 `resultStatus`；正式事实仍是业务真相源。未发布考试成绩
MUST 不展示、不计分；已发布考试结果 MUST 按 Candidate 的 PASSED/FAILED 正式结论映射。成绩聚合枚举和算法 MUST 与 OpenAPI 的
AVERAGE/WEIGHTED_AVERAGE 一致。最小版本中 EXAM/QUESTION_BANK MUST NOT 重复计入培训学时。最终快照 schemaVersion=3 MUST
保留两个状态；快照 MUST 包含 finalizedTime，并与投影及参训人的归档时间保持一致。培训绑定来源事实写入后 MUST
自动执行上述投影更新；计划级同步保留为全量补账，MUST NOT 再作为看见正式结果的唯一途径。

#### Scenario: 同步正式结果

- **GIVEN** 课程完成、考试成绩发布或线下成绩保存
- **WHEN** 系统同步培训结果
- **THEN** 系统更新唯一逐内容投影及参训人聚合字段，并保留可追溯的正式来源

#### Scenario: 考试成绩未发布

- **GIVEN** Candidate 已交卷但成绩未发布
- **WHEN** 同步或查询培训结果
- **THEN** 系统显示待发布且不计入 examScore

#### Scenario: 投影区分来源状态与培训结论

- **GIVEN** 来源考试 Candidate 状态与培训统一结论使用不同枚举
- **WHEN** 系统同步逐内容投影或冻结最终快照
- **THEN** `sourceStatus` 保存来源记录的原始状态
- **AND** `resultStatus` 保存培训统一结论
- **AND** schemaVersion=3 的最终快照同时保留这两个字段

#### Scenario: 计划级同步仍可全量补账

- **GIVEN** 历史投影落后于来源事实
- **WHEN** 管理员调用计划级结果同步
- **THEN** 系统按每名未冻结参训人、每个内容项刷新投影并重建聚合
- **AND** 已冻结参训人不得覆盖

### Requirement: 来源事实写入后自动刷新对应逐内容投影

系统 MUST 在培训绑定的课程学习状态变化、培训考试/题库开考或交卷或成绩发布、线下成绩保存之后，于同一事务按
`trainingParticipantId + planContentId` 幂等刷新唯一逐内容投影，并重建该参训人聚合字段。系统 MUST NOT
要求管理员先调用计划级结果同步，学员或查询才能看到更新后的内容项状态。无培训参训人的独立开课或独立考试 MUST NOT
刷新培训投影。已冻结参训人投影 MUST NOT 被此次刷新覆盖。学员与结果查询 MUST 继续只读投影，MUST NOT 在读路径现场同步。计划级
`synchronize` MUST 仍可用于全量补账；计划结束 MUST 仍先全量同步再冻结。系统 MUST NOT 因单条内容完成而写入参训人最终
PASSED/FAILED。线下签到 MUST NOT 再作为看见线下完成结果的必要写入。

#### Scenario: 学员交卷后内容项不再保持待开始

- **GIVEN** 培训考试或题库考生已生成 PENDING 投影
- **WHEN** 学员交卷且来源进入已提交或待发布
- **THEN** 对应投影 `resultStatus` 为 `IN_PROGRESS` 或 `PENDING_PUBLISH`
- **AND** 查询学员参训详情或结果不必先调用计划级结果同步
- **AND** 未发布成绩仍不计入 `examScore`

#### Scenario: 课程学习状态变化后投影跟随

- **GIVEN** 培训报名且投影为 PENDING
- **WHEN** 学习状态首次变为 LEARNING
- **THEN** 对应投影为 `IN_PROGRESS`
- **WHEN** 学习状态变为 COMPLETED
- **THEN** 对应投影为 `COMPLETED` 且 `onlineHours` 按课程学习秒数重建
- **AND** 仅学习秒数变化而状态不变时 MUST NOT 为刷新投影而额外写入

#### Scenario: 教师保存成绩后投影跟随

- **GIVEN** 线下内容已有 PENDING 投影
- **WHEN** 场次教师保存该参训人已评分成绩
- **THEN** 系统在同一事务刷新该内容项投影及该参训人线下聚合
- **AND** 查询结果不必先调用计划级结果同步
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
为 0；EXAM/QUESTION_BANK MUST 为 0 且 MUST NOT 计入 `onlineHours`。QUESTION_BANK 执行配置 `durationSeconds` MUST
仅表示作答时限，MUST NOT 作为培训学时。计划结束 MUST NOT 用个人获得学时重算 TOTAL_HOURS 或线上占比。线下获得学时 MUST NOT
再从出勤状态派生。

#### Scenario: 发布按安排学时校验

- **GIVEN** 计划含课程、考试、题库和线下，各填了 requiredHours
- **WHEN** 管理员发布
- **THEN** 系统按内容 requiredHours 合计总学时，并将课程/考试/题库计入线上占比
- **AND** 不读取任何学员获得学时

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

### Requirement: 培训进入不采用客户端作答类型

学员从培训上下文进入 EXAM/QUESTION_BANK 时，系统 MUST 只使用参训人身份、内容项、客户端令牌和设备信息。系统 MUST NOT 根据请求
`attemptType` 创建答卷。已有该考生非作废终态时 MUST 返回原 `examAttemptId`。补考 MUST 使用考试域补考接口，不得经 enter 声明
RETAKE。

#### Scenario: 培训考试交卷后 enter

- **GIVEN** 培训考试已有已提交或待发布答卷
- **WHEN** 学员再次 enter 并传 `attemptType=FIRST`
- **THEN** 返回既有 `examAttemptId`，不新增首次作答

### Requirement: 学员可读取本人线下场次详情

系统 MUST 提供学员只读线下场次详情，权限 `training:learner:task`。请求 MUST NOT 携带 `userId`。系统 MUST
用当前登录用户在该场次所属计划上的参训记录定位本人；计划 MUST 为 RUNNING 或 ENDED。响应 MUST
包含名称、地点、起止以及本人已评分成绩与投影 `resultStatus` / `effectiveDurationSeconds`。MUST NOT 返回出勤或签到窗口。MUST
NOT 返回他人名单或管理端编排字段（教师用户 ID、乐观锁版本）。场次不存在、当前用户未参训或计划对学员不可见时 MUST 返回 404
且业务码 8155。统一进入 OFFLINE MUST 仅返回跳转用 `offlineSessionId`；ENDED 计划 MUST 仍拒绝 enter，但本详情 GET MUST 可读。

#### Scenario: 参训学员读取本人场次

- **GIVEN** 当前用户是该场次计划的参训人，计划为 RUNNING，已评分
- **WHEN** 调用学员场次详情
- **THEN** 返回名称、地点、起止、本人成绩和投影学时/结果状态

#### Scenario: 未参训不可见

- **GIVEN** 场次存在但当前用户不是该计划参训人
- **WHEN** 调用学员场次详情
- **THEN** 系统返回 404/8155
- **AND** 不返回场次编排数据

#### Scenario: 结束后仍可查看

- **GIVEN** 本人参训且计划为 ENDED
- **WHEN** 调用学员场次详情
- **THEN** 系统返回该场次本人详情

### Requirement: 计划结束必须满足门禁并冻结结果

计划只有处于 RUNNING，且必修执行已生成、无进行中事实、全部逐内容投影同步完成、冻结的计划编排标准已在发布时满足、适用的正式综合结果存在时才能
ENDED。当前 TOTAL_HOURS、ONLINE_RATIO 和 onlineMaxRatio MUST 作为计划编排合规规则，不得被重新解释为个人实学时门槛；个人结论
MUST 由必修逐内容正式结论和适用的正式综合结果产生。结束时 MUST 冻结每名参训人的逐内容投影和最终结论；快照 MUST 带
schemaVersion、完整来源和规范化 hash，finalized 后普通同步 MUST NOT 覆盖投影或快照。

#### Scenario: 正常结束

- **GIVEN** 所有必修内容有终态正式结果且标准规则满足
- **WHEN** 管理员携带当前版本结束计划
- **THEN** 系统冻结 finalResultSnapshot、确定 PASSED/FAILED/INCOMPLETE 并推进计划为 ENDED

#### Scenario: 已发布但未开始的计划不能正常结束

- **GIVEN** 计划处于 PUBLISHED 且尚未显式 start
- **WHEN** 管理员结束计划
- **THEN** 系统返回 409/8163，且不执行结果同步、冻结或状态更新

#### Scenario: 混合计划不把考试时长重复计入个人学时

- **GIVEN** 计划发布时已满足计划学时与线上占比，且参训人已完成 COURSE、EXAM、QUESTION_BANK 和 OFFLINE 全部必修内容
- **WHEN** 系统同步并结束计划
- **THEN** 系统按逐内容正式结论确定人员结果，EXAM/QUESTION_BANK 不计入个人学时且不会因此误判为未满足计划标准

#### Scenario: 存在进行中考试

- **GIVEN** 某参训人的 Attempt 仍在进行中
- **WHEN** 管理员结束计划
- **THEN** 系统拒绝结束并返回 participantId、contentId 和阻塞原因

#### Scenario: 取消计划

- **GIVEN** 计划提前终止且已有部分事实
- **WHEN** 管理员取消计划
- **THEN** 系统保留已有事实且不把参训结果标为正常通过

### Requirement: 培训数据库资产可复现

培训结构和正式类型 seed MUST 同时存在于 init 最终 SQL 和 Admin Versioned Migration，并同步 `sts.init.schema-version`。

#### Scenario: 空库和升级库初始化

- **GIVEN** 一个空库和一个升级前已有库
- **WHEN** 分别执行 init 与 migration
- **THEN** 两者获得一致培训结构和不少于 25 种稳定编码的正式培训类型

### Requirement: 培训闭环必须有真实纵向验收证据

系统 MUST 使用真实 MySQL 和最小 Spring/MyBatis 装配验证一个包含 COURSE、EXAM、QUESTION_BANK、OFFLINE 的计划从发布到结束的完整链路。Mapper
约束测试和 mock Service 测试 MUST NOT 替代该验收。

#### Scenario: 四类内容真实混合计划完成

- **GIVEN** 一个机构、一名参训人和四类可用资源
- **WHEN** 系统依次发布计划、生成执行、显式开始、写入正式事实、同步结果并结束
- **THEN** 四类真实执行、唯一投影、聚合结果、ENDED 状态和带 hash 的 v3 最终快照均可在真实 MySQL 中核验

### Requirement: 线下计划与场次不得包含签到配置

系统 MUST 仅以教师、地点、授课起止时间和及格线判断线下计划内容能否发布。批量生成和单内容补建 MUST
创建不含签到窗口、签退要求或签到规则的线下场次。管理端创建、更新和查询场次的契约 MUST NOT 接受或返回签到字段。

#### Scenario: 无签到配置发布线下内容

- **GIVEN** 线下内容已配置教师、地点、授课起止时间和及格线，但没有签到窗口、签退要求或签到规则
- **WHEN** 管理员执行发布预检或正式发布
- **THEN** 系统不得因缺少签到字段返回校验问题

#### Scenario: 线下必需信息不完整

- **GIVEN** 线下内容缺少教师、地点、授课起止时间或及格线之一
- **WHEN** 管理员执行发布预检或正式发布
- **THEN** 系统返回“线下内容必须配置教师、地点、时间和及格线”

#### Scenario: 两种入口生成线下场次

- **GIVEN** 已发布计划包含配置完整的线下内容
- **WHEN** 管理员批量生成执行或补建单个内容执行
- **THEN** 两种入口均创建或复用同一线下场次
- **AND** 场次仅包含名称、教师、地点、起止时间、状态及计划/阶段/内容关联

### Requirement: 系统不得保留签到存储与待确认统计

系统 MUST 删除线下场次的签到列和独立签到记录表。参训人删除保护 MUST NOT 查询签到记录；教师工作台 MUST NOT 查询或返回待确认签到数量，并
MUST 继续返回教师线下场次数量。

#### Scenario: 教师查询工作台

- **GIVEN** 当前教师存在已分配的线下场次
- **WHEN** 教师查询工作台摘要
- **THEN** 响应包含线下场次数量
- **AND** 响应不包含待确认签到数量

#### Scenario: 删除没有其他下游事实的草稿参训人

- **GIVEN** 草稿计划参训人没有成绩、奖惩、课程报名、考试人员或正式计算事实
- **WHEN** 管理员移除该参训人
- **THEN** 系统不查询签到表并允许按既有规则移除
