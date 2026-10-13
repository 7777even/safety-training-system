# Training Minimum Execution Loop

## ADDED Requirements

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
形成题库练习，不得替换为专项练习。最小版本的 QUESTION_BANK MUST 只包含可自动判分题型。

#### Scenario: 生成真实执行

- **GIVEN** 已发布计划包含四类内容
- **WHEN** 管理员生成执行
- **THEN** 系统创建真实 CourseOffering/Enrollment、计划专属 Exam/AssessmentExecution/Candidate、BANK_PRACTICE
  Execution/Candidate 和 OfflineSession

#### Scenario: 题库练习缺少配置

- **GIVEN** QUESTION_BANK 内容缺少时长、及格线、次数或抽题规则
- **WHEN** 管理员发布计划
- **THEN** 系统拒绝发布且不猜测业务默认值

### Requirement: 培训执行幂等且上下文隔离

每个 planContent MUST 只有一个真实执行；重复生成不得重复创建实例。EXAM/QUESTION_BANK MUST 使用计划专属
AssessmentExecution，不能共享来源考试 Candidate，也不能用资源 ID 冒充 executionId。

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

### Requirement: 线下事实只能由合法教师处理

系统 MUST 支持线下签到、签退、成绩、附件和奖惩；只有场次教师或明确授权的培训管理员可以处理。要求签退但未签退的记录不得计入完整学时。

#### Scenario: 教师处理本人场次

- **GIVEN** 当前教师被指派到目标线下场次
- **WHEN** 教师确认出勤并提交成绩
- **THEN** 系统保存登录执行人、时间、证据和正式线下事实

#### Scenario: 教师处理他人场次

- **GIVEN** 当前教师未被指派且没有培训管理权限
- **WHEN** 读取或修改目标场次
- **THEN** 系统返回 403 且不写入数据

### Requirement: 培训结果由正式来源同步到逐内容投影

系统 MUST 按 trainingParticipantId + planContentId 读取课程、考试和线下正式事实，并幂等更新唯一的逐内容结果投影。投影 MUST
保存 executionId、来源记录、学时、分数和完成状态；正式事实仍是业务真相源。未发布考试成绩 MUST 不展示、不计分；最小版本中
EXAM/QUESTION_BANK MUST NOT 重复计入培训学时。

#### Scenario: 同步正式结果

- **GIVEN** 课程完成、考试成绩发布或线下事实确认
- **WHEN** 系统同步培训结果
- **THEN** 系统更新唯一逐内容投影及参训人聚合字段，并保留可追溯的正式来源

#### Scenario: 考试成绩未发布

- **GIVEN** Candidate 已交卷但成绩未发布
- **WHEN** 同步或查询培训结果
- **THEN** 系统显示待发布且不计入 examScore

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
- **THEN** 四类真实执行、唯一投影、聚合结果、ENDED 状态和带 hash 的 v2 最终快照均可在真实 MySQL 中核验
