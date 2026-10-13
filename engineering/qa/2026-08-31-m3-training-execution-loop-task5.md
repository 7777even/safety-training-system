# QA：M3 培训执行闭环 Task 5

## 范围

OpenSpec Change `2026-07-29-complete-m3-training-execution-loop` Task 5。仅后端与 OpenAPI。对照 Design 第 15
节补齐计划专属批阅与真实来源状态。

## 已确认规则

- EXAM `resourceSnapshotJson` 使用 schemaVersion=2，完整冻结计划专属 Exam、Policy、AssessmentExecution 和 Reviewer
  字段，并校验规范化 hash。
- paperVersion 含非自动判分题时，没有本机构有效 reviewer 必须阻止发布；纯自动判分允许 reviewer 为空。
- provision 不得读取来源 Exam、ExamPolicy、来源 AssessmentExecution 或来源 Reviewer；v1 或缺字段快照返回 8142，提示取消后重建计划。
- 计划专属 Exam、完整 Policy（含补考起止时间）、AssessmentExecution 和 Reviewer 同一事务创建；Reviewer 绑定新
  `assessmentExecutionId`。
- 重复 provision 仅在 `planContentId`、`trainingResourceSnapshotHash` 和 Reviewer 集合一致时复用；不一致返回冲突且不覆盖。
- 投影持久化真实 `sourceStatus`；考试 PASSED→培训 COMPLETED，FAILED→FAILED；OpenAPI 不得宣称 sourceStatus 等于
  resultStatus。
- 新最终快照 schemaVersion=2 且 item 含 `sourceStatus`；已归档 v1 快照不重写；归档后查询按 schemaVersion 解析冻结快照。
- `TRAINING_PRACTICE` 缺少有效 drawRules 或 AssessmentExecution 快照 hash 不匹配时拒绝创建 Candidate/Attempt；
  `COURSE_BANK_PRACTICE` 无规则全量练习不变。
- 数据库只新增可空 `source_status`；同步 init 完整 SQL、新 V 文件、Entity 和 schema-version；禁止修改既有 V 文件。

## 复核缺口与补测（2026-08-31 二轮）

只读复核曾判定 Task 5 不能完成，已按 TDD 补齐：

1. 计划专属 ExamPolicy 回填 `retakeStartTime` / `retakeEndTime`（ISO 解析）。测试：
   `TrainingAssessmentExecutionAdapterTest.ensureExamExecution_copiesRetakeWindowFromSnapshot`。
2. 开考前统一校验 AssessmentExecution 快照完整性：`policySnapshotJson` 被改但 `snapshotHash` 未同步时 7063，且不创建
   Candidate/Attempt。测试：`ExamAttemptServiceImplTest.start_trainingPractice_rejectsTamperedPolicySnapshotHash`。
3. 版本化最终快照解析器支持 v1/v2；归档后查询优先读冻结快照；再次冻结不改写 v1。测试：
   `getResults_archivedV1Snapshot_readableWithoutGuessingSourceStatus`、`freezePlan_existingV1Snapshot_notRewritten`。

上述四条测试先观察失败，再最小实现。

## 复核缺口与补测（2026-08-31 三轮）

归档最终快照读取增加 hash 完整性校验，不扩展其他能力：

- 仅 `resultFinalizedTime != null` 且存在最终快照时校验 `SHA-256(finalResultSnapshotJson UTF-8)`，必须与
  `finalResultSnapshotHash` 完全一致。
- hash 缺失、非 64 位或不一致：HTTP 500 / 8111「最终结果快照完整性校验失败」；不返回被篡改快照、不回退投影、不重新生成、不改写任何正式事实。
- hash 通过后再交给 `TrainingFinalResultSnapshotParser`；v1 缺少 sourceStatus 保持 null，不升级、不重序列化。
- 未归档继续读实时投影，不要求最终快照 hash。
- 定向测试 `TrainingResultSyncServiceImplTest` 17/17，覆盖合法 v1/v2、篡改 v1/v2、非法 hash、未归档投影。

## 验证结果

- 目标零 DB（三轮补测后）：`TrainingResultSyncServiceImplTest` 17/17。
- 完整 Admin 零 DB：841，0 失败、0 错误、1 跳过。
- 带 DB IT：无 schema 变化，沿用本 Task 首轮完整 Admin MySQL IT 206/206、Admin-init 1/1（baseline `20260831180000`）。
- `openspec validate 2026-07-29-complete-m3-training-execution-loop --strict` 通过；`git diff --check` 通过。未改
  `frontend/`、OpenAPI、migration 或解析器职责。

## Review

- 规格符合性：通过。Design 第 15.2～15.6 以及最终快照读取完整性：合法 v1/v2 可读取，篡改或 hash 非法
  8111，失败不回退投影。QUESTION_BANK 复用仍按 Task 2 的 planContentId 唯一键幂等。
- 代码质量：通过。hash 校验留在 `TrainingResultSyncServiceImpl` 读取编排层，解析器只负责 schemaVersion。未改数据库、OpenAPI、前端。
- 需人工决策：无。管理员代办权限仍不开放（沿用 Task 4）。
