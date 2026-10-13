# QA：M3 培训执行闭环 Task 6

## 范围

OpenSpec Change `2026-07-29-complete-m3-training-execution-loop` Task 6。仅后端与 OpenAPI。对照 Design 第 16
节收紧结束状态机、钉住培训标准/个人结论口径，并补齐四类内容真实混合计划 MySQL 纵向 IT。无数据库结构变化，不新增路径或字段。

## 已确认规则

- 正常生命周期：`DRAFT → PUBLISHED → RUNNING → ENDED`，旁路 `CANCELLED`。
- `end` 只接受 RUNNING。PUBLISHED 直接结束返回 409/8163，且不调用同步、冻结或计划更新。
- `TOTAL_HOURS` / `ONLINE_RATIO` / `onlineMaxRatio` 只在发布时按计划 `requiredHours` 校验；结束时不按个人
  `onlineHours/offlineHours` 重算。
- 个人 PASSED/FAILED/INCOMPLETE 只看必修逐内容终态和适用 COMPOSITE；EXAM/QUESTION_BANK 不累计培训学时。
- 真实混合 IT 覆盖一个机构、一名参训人、四个必修内容：发布→四类执行→显式 start→正式事实→投影同步→end→v2 快照/hash→归档后不可变；另覆盖
  PUBLISHED 直接 end 零副作用。

## TDD 与实现

1. `TrainingPlanLifecycleServiceImplTest.end_publishedPlan_rejectsWithoutSyncOrFreeze`：先观察 8104（越过状态机走到版本更新），再把
   `end` 改为仅 RUNNING，消息为「仅进行中的计划可结束」。进行中 Attempt 用例改为 `runningPlan()`。
2. 发布学时：`totalHoursBelowStandard` / `onlineRatioExceedsStandard` / `mixedExamHoursMeetStandard`。
3. 个人结论：`freezePlan_examWithoutTrainingHours_stillPassed`，考试 0 学时仍 PASSED，`onlineHours` 只来自课程。
4. `TrainingMixedPlanLifecycleIT`：真实 Mapper + 发布/生成/生命周期/同步 Service；因 Provisioner `REQUIRES_NEW` 不加类级
   `@Transactional`，`@AfterEach` 清理已提交行。
5. MySQL JSON 列会规范化键序和数字尾零。混合 IT 红灯后最小修复：`AssessmentExecutionSnapshotIntegrity` 对 policy Map
   做稳定键序后再算 hash；EXAM 发布快照在冻结与校验前规范化 BigDecimal，并用类型化 round-trip 比对 hash。不改 migration。

## 验证结果

- 目标零 DB：`TrainingPlanLifecycleServiceImplTest` 6、`TrainingPlanPublishValidatorTest` 11、
  `TrainingResultSyncServiceImplTest` 18、`AssessmentExecutionSnapshotIntegrityTest` 1、`TrainingExecutionProvisionerTest`
  7。
- 完整 Admin 零 DB：`mvn -pl sts-service-admin -am test` → Tests run: 847, Failures: 0, Errors: 0, Skipped: 1（
  `QpdfRealProcessTest`）。
- 完整 Admin MySQL IT：`mvn -pl sts-service-admin -am -Pintegration-test test` → Tests run: 208, Failures: 0, Errors: 0,
  Skipped: 0；其中 `TrainingMixedPlanLifecycleIT` 2/2。
- Admin-init IT：`mvn -pl sts-service-admin-init -am -Pintegration-test test` → Tests run: 1, Failures: 0, Errors: 0,
  Skipped: 0；Flyway baseline `20260831180000`。
- `openspec validate 2026-07-29-complete-m3-training-execution-loop --strict`：valid。
- `docs/api/training.openapi.json` JSON 可解析；`git diff --check` 通过。
- 未改 `frontend/`、migration 或 `sts.init.schema-version`。

## Review

- 规格符合性：通过。Design 16.1～16.3：PUBLISHED 不可 end（409/8163 且不同步/冻结/更新）；学时口径钉在发布校验与冻结测试；四类混合计划真库纵向验收补齐
  Task 3 缺口；OpenAPI end 说明已同步。
- 代码质量：通过。状态机只改 `end` 允许集。JSON hash 规范化限于完整性算法，不扩大业务能力。混合 IT 显式清理 REQUIRES_NEW
  提交，避免污染共享容器。无越界重构、无新增 API/权限、无 schema 变更。
- 需人工决策：无。结果更正、管理员代办、发布后追加名单仍不开放。
