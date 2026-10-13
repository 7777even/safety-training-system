# Tasks：培训后端最小可用闭环

> 状态：Task 1～6 已实施。Owner 为 `lims`；全部 Task 仅后端，前端以 OpenAPI 为准另开。

> 已确认决策（2026-08-31）：EXAM/QUESTION_BANK 的 executionId 统一使用 AssessmentExecution ID；培训引用考试时创建计划专属
> Exam，不共享来源考试执行；当前最小版本发布后参训名单只读，追加方案仅记录在 Design 第 12 节；当前直接建立逐内容结果投影表，并使用版本化、带
> hash 的最终结果快照。

## Task 1：发布培训计划并冻结真实资源版本

- 状态：已完成
- Owner：lims
- 输入：既有培训类型/标准/模板/计划；课程版本；考试试卷版本；题库；评价表单版本；具体参训人。
- 输出：模板/计划内容版本字段、发布预检、完整结构快照、正式培训类型初始化。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：既有类型/标准/模板/计划 API；培训可引用资源查询；课程、考试、题库只读校验。
- 数据影响：模板内容增加 resourceVersionId、assessmentMode、executionConfig；计划内容增加相同字段及
  resourceSnapshot/hash；补不少于 25 种正式培训类型 seed；同步 init SQL、Versioned Migration 和 schema-version。
- 验收标准：
  - [x] 空库与升级库都能幂等获得不少于 25 种稳定编码的正式培训类型。
  - [x] 模板和计划支持 COURSE、EXAM(QUIZ/FORMAL_EXAM)、QUESTION_BANK、OFFLINE。
  - [x] 发布前校验资源机构、发布状态、执行配置、必修内容、参训人和标准学时/线上占比。
  - [x] COURSE 冻结 courseVersionId；EXAM 冻结 paperVersionId 和来源策略；QUESTION_BANK 冻结题库引用与练习配置；OFFLINE
    冻结场次规则。
  - [x] 发布后计划结构和参训名单不可修改，来源资源新版本不影响已发布计划。
- 测试方式：先写发布校验、快照、版本稳定和 seed 幂等失败测试；运行目标零 DB、培训/资源相关 MySQL IT、OpenAPI 严格校验和
  `git diff --check`。

## Task 2：生成四类真实培训执行

- 状态：已完成
- Owner：lims
- 输入：Task 1；课程开课/报名；AssessmentExecution、Candidate、Attempt；线下场次。
- 输出：TrainingExecutionService、课程/考试最小内部 Port、计划级生成、单内容重试和真实 execution link。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：generate-executions、单内容 provision、执行列表；课程/考试内部 Port。
- 数据影响：EXAM/QUESTION_BANK executionId 改指 AssessmentExecution；CourseOffering、计划专属 Exam 和 OfflineSession 增加或确认
  planContentId 唯一约束；同步 init、migration、schema-version。
- 验收标准：
  - [x] 培训域不直接写课程/考试 Mapper，只通过内部 Port 调用。
  - [x] COURSE 创建/复用冻结版本 CourseOffering，并为所有参训人创建 TRAINING Enrollment。
  - [x] EXAM 创建计划专属 Exam/AssessmentExecution/Candidate，不共享来源考试 Candidate。
  - [x] QUESTION_BANK 仅允许可自动判分题型，创建 BANK_PRACTICE Exam/AssessmentExecution/Candidate，首次作答冻结题目快照。
  - [x] OFFLINE 创建/复用包含教师、时间、地点和签到规则的场次。
  - [x] executionId 指向真实执行实例，不使用 courseId、来源 examId 或 questionBankId。
  - [x] 重复、并发和失败重试不重复创建实例；单内容失败不回滚其他成功内容。
- 测试方式：TDD；Coordinator/Port 零 DB，真实 CourseOffering/Enrollment、Exam/AssessmentExecution/Candidate、QUESTION_BANK
  和唯一键 MySQL IT，OpenAPI/实现差异检查及双轴 Review。

## Task 3：完成学员教师流程、正式结果同步和计划结束

- 状态：已完成（真实混合计划验收由 Task 6 补齐）
- Owner：lims
- 输入：Task 2；课程正式学习结果；考试正式发布成绩；线下签到/成绩；评价域正式综合结果。
- 输出：学员本人任务/详情/统一进入、教师本人任务、逐内容结果投影与同步、计划 start/end/cancel 和最终结果快照。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：学员 tasks/detail/enter/results；教师任务和线下处理；results/synchronize；plan start/end/cancel。
- 数据影响：新增 `sts_training_participant_content_result`；TrainingParticipant 增加
  finalResultSnapshotJson、finalResultSnapshotHash、resultFinalizedTime；同步 init、migration、schema-version。
- 验收标准：
  - [x] 学员只查看和进入本人培训内容；enter 不接受 userId、examId、assessmentExecutionId 或 contextType。
  - [x] 教师只能处理本人场次；签到/签退、成绩和执行人均来自真实登录态与事实。
  - [x] 每个 participantId + contentId 存在唯一投影行；结果同步从 Enrollment、Candidate/Attempt、Attendance/OfflineScore
    幂等更新来源 ID、学时、分数和状态。
  - [x] 未发布考试成绩不展示、不计分；要求签退但未签退时不计完整线下学时。
  - [x] 重复同步不重复、不清空正式结果；参训聚合字段由逐内容投影重建，EXAM/QUESTION_BANK 不重复计入培训学时。
  - [x] 必修执行未生成、存在进行中事实、结果不完整或正式综合结果缺失时不能结束，并返回具体阻塞项。
  - [x] 正常结束先校验并冻结全部投影，再生成带 schemaVersion、逐内容来源和规范化 hash 的 finalResultSnapshot，并确定
    PASSED/FAILED/INCOMPLETE；finalized 后普通同步不得覆盖投影或快照；取消保留事实且不产生正常通过结果。
  - [x] 完成真实混合计划后端验收：发布→四类执行→学员/教师事实→结果同步→结束；现有 Mapper IT 与 mock 测试不能替代，转 Task 6
    补齐。
  - [x] 更新 tasks.md、进度表、QA 和 Retro；双轴 Review 通过后才能把 M3 后端标记完成。
- 测试方式：TDD；身份/权限/同步/门禁零 DB，课程/考试/线下来源和结果冻结 MySQL IT，Admin 受影响测试、OpenAPI strict、OpenSpec
  strict 与 `git diff --check`。

## Task 4：修正执行与结果闭环偏差

- 状态：已完成
- Owner：lims
- 输入：Task 1～3 当前实现；Design 第 14 节；课程、考试、题库和线下正式事实；2026-08-31 代码与 Docker/MySQL 集成测试复核结论。
- 输出：可复现的题库抽题与计分、从发布快照创建的计划专属考试、准确的考试/线下结果投影、教师本人场次护栏、契约一致的聚合结果、自描述且不可变的最终快照。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：沿用既有培训执行、学员进入、教师线下处理、结果同步和计划结束 API；不新增路径或请求/响应结构，教师接口的
  `x-permission` 与实现统一修正为已确认的 `training:teacher:task`。除该规格纠偏外，不改变 API
  Contract、权限模型或数据库结构；若实现确认还必须变化则暂停并取得人工确认。
- 数据影响：原则上无数据库结构变化；复用既有 execution config、资源快照、Attempt 题目快照、逐内容结果投影和最终快照字段。
- 验收标准：
  - [x] QUESTION_BANK 首次 Attempt 严格执行冻结 drawRules，按规则过滤、抽取和赋分，持久化随机种子与题目快照；候选不足明确失败，重复进入不重新抽题。
  - [x] QUESTION_BANK 的总分、passScore、maxAttempts 和 Candidate 最终结论一致，不能出现配置上永远无法通过的练习。
  - [x] 计划专属 EXAM 只使用校验后的发布快照复制 paperVersion 和考试策略；来源考试后续变化不影响已发布计划。
  - [x] 已发布 Candidate PASSED/FAILED 分别稳定映射为 COMPLETED/FAILED；未发布结果不计分且不能满足结束门禁。
  - [x] OFFLINE 必须具有符合配置的确认出勤，配置及格线时还必须具有正式成绩；孤立成绩不能形成终态。
  - [x] 教师使用 `training:teacher:task` 且只能处理本人场次；普通接口权限不能自动绕过数据归属，管理员代办未获人工确认前不开放。
  - [x] `AVERAGE` 与 `WEIGHTED_AVERAGE` 严格按 OpenAPI 枚举和公式执行，并覆盖权重及缺少正式成绩的边界。
  - [x] finalResultSnapshot 包含 finalizedTime；投影、快照和参训人的归档时间一致，归档后同步不改变快照或 hash。
  - [x] 修复 FlywayMigrationIT 对复合索引的断言；Admin 零 DB、Admin MySQL IT（205）、Admin-init MySQL IT（1）、OpenAPI/OpenSpec
    与 `git diff --check` 全部通过。
  - [x] 完成规格符合性和代码质量双轴 Review；复核 Task 1～3 的相关勾选、进度表、QA 和 Retro，仅在证据一致后恢复 M3 完成状态。
- 测试方式：严格 TDD；先增加或修正题库抽题/计分、冻结策略、结果映射、线下门禁、教师归属、加权聚合、快照不可变性和复合索引失败测试并观察失败，再完成最小实现；运行受影响零
  DB 测试及完整 Admin/Admin-init MySQL IT。

## Task 5：补齐计划专属批阅与真实来源状态

- 状态：已完成
- Owner：lims
- 输入：Task 1～4 当前实现；Design 第 15 节；来源 Exam/Policy/Reviewer、不可变 paperVersion、逐内容结果投影和最终快照。
- 输出：自足的 EXAM v2 发布快照、计划专属 Reviewer、无来源 Exam 运行期依赖的执行生成、真实 sourceStatus 投影、v2
  最终快照和培训题库严格运行期防线。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：不新增路径；修正培训结果 item 的 `sourceStatus` 语义并标记可空，其他请求/响应结构保持不变。
- 数据影响：`sts_training_participant_content_result` 增加可空 `source_status`；必须同步 init 完整 SQL、新增 Admin
  Versioned Migration、Entity、Mapper/VO、最终快照 schemaVersion 和 `sts.init.schema-version`，禁止修改既有 V 文件。
- 验收标准：
  - [x] EXAM `resourceSnapshotJson` 使用 schemaVersion=2，完整冻结计划专属 Exam、Policy、AssessmentExecution 和 Reviewer
    所需字段并校验规范化 hash。
  - [x] paperVersion 含非自动判分题目时，没有本机构有效 reviewer 必须阻止计划发布；纯自动判分考试允许 reviewer 为空。
  - [x] provision 不读取来源 Exam、ExamPolicy、来源 AssessmentExecution 或来源 Reviewer；来源修改、停用或删除不影响 v2
    快照创建执行。
  - [x] 计划专属 Exam、完整 Policy、AssessmentExecution 和 Reviewer 在同一事务创建；Reviewer 绑定新
    assessmentExecutionId，主观题能够完成批阅和成绩发布。
  - [x] 重复 provision 只在 planContentId、trainingResourceSnapshotHash 和 Reviewer 集合一致时复用，不一致返回冲突且不覆盖既有执行。
  - [x] 投影持久化真实 `sourceStatus`，考试 PASSED/FAILED 与培训 COMPLETED/FAILED 保持可区分；OpenAPI 不再宣称
    sourceStatus 等于 resultStatus。
  - [x] 新生成最终快照使用 schemaVersion=2 且 item 包含 sourceStatus；既有已归档 v1 快照保持不可变并可读取。
  - [x] `TRAINING_PRACTICE` 缺少有效 drawRules 或快照校验失败时拒绝开考；`COURSE_BANK_PRACTICE` 的既有无规则全量练习不受影响。
  - [x] init SQL 与新 migration 得到相同结构；未归档旧投影可由同步补齐 sourceStatus，已归档 v1 数据不猜测、不改写。
  - [x] Admin 零 DB（841，0 失败、1 跳过）、Admin MySQL IT（206）、Admin-init MySQL IT（1，baseline `20260831180000`
    ）、OpenAPI/OpenSpec 与 `git diff --check` 全部通过，并完成规格符合性与代码质量双轴 Review。
- 测试方式：严格 TDD；先覆盖主观题无 reviewer 发布失败、v2 快照冻结、来源删除后 provision、计划专属 reviewer
  批阅、幂等指纹冲突、sourceStatus 双状态、v1/v2 快照兼容和两类题库分流的失败测试，再完成最小实现；数据库字段、映射、migration
  和空库一致性使用真实 MySQL IT 验证。

## Task 6：收紧结束状态机并补齐真实混合验收

- 状态：已完成
- Owner：lims
- 输入：Task 1～5 当前实现；Design 第 16 节；2026-08-31 整体只读复核结论。
- 输出：只允许 RUNNING 正常结束的生命周期护栏、明确且有测试的培训标准/个人结果口径、一条四类内容真实混合计划 MySQL 纵向
  IT，以及一致的 OpenAPI、QA、Retro 和进度记录。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：不新增路径或字段；`POST /api/training/plans/{trainingPlanId}/end` 补充“仅 RUNNING 可结束”和 409/8163 说明。
- 数据影响：无数据库结构和正式初始化数据变化；不得新增或修改 migration。
- 验收标准：
  - [x] PUBLISHED 计划直接 end 返回 409/8163，且不调用结果同步、冻结或计划更新；只有 RUNNING 可正常结束。
  - [x] 发布阶段继续按冻结的计划 requiredHours 校验 TOTAL_HOURS/ONLINE_RATIO/onlineMaxRatio；个人结论不把这些计划规则误用为个人实学时门槛。
  - [x] PASSED/FAILED/INCOMPLETE 严格由必修逐内容正式结论和适用的正式 COMPOSITE 结果产生；包含不计学时的
    EXAM/QUESTION_BANK 时不会因学时口径误判。
  - [x] 增加真实 MySQL 混合计划 IT，贯通发布→四类执行→显式 start→学员/教师正式事实→投影同步→end→v2 快照/hash→归档后不可变。
  - [x] 混合 IT 同时验证计划专属 AssessmentExecution/Candidate、executionId 类型、重复生成幂等、逐内容唯一投影和 finalized
    一致性。
  - [x] OpenAPI end 说明、Proposal 状态、进度表数量与阻塞项、Task 6 QA/Retro 同步；不得保留“待人工确认”“836”或“IT 待
    Docker”等过期完成证据。
  - [x] 目标零 DB、完整 Admin 零 DB（847/0 失败/1 跳过）、完整 Admin MySQL IT（208）、Admin-init IT（1，baseline `20260831180000`
    ）、OpenSpec strict、OpenAPI JSON 和 `git diff --check` 全部通过；规格符合性与代码质量双轴 Review 均为通过。
- 测试方式：严格 TDD；先增加 PUBLISHED 直接 end 失败测试和混合计划纵向 IT 并观察失败，再做最小实现；运行受影响零 DB、完整
  Admin/Admin-init MySQL IT 和规格/契约静态验证。

## 暂不执行

岗位矩阵；参训人导入、扫码、导出和发布后追加；审批/通知；独立统计大屏；问卷、综合计算算法、档案、证书和监管预警自身能力；任何前端实现。
