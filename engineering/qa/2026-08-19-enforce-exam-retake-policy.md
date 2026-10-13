# QA：考试补考策略护栏

## 范围

OpenSpec Change `2026-08-19-enforce-exam-retake-policy` Task 1。按既有 `sts_exam_policy` 落实补考开新尝试护栏，无
migration。

## 缺陷与修复

- 补考接口原先只检查是否启用和总作答次数，未校验正式发布、范围、补考窗口和考试结束后补考。
- 补考次数改为统计非作废 `RETAKE` 尝试；首考不再单独导致超限。
- `ENDED` 允许补考；`DRAFT`/`CANCELLED` 返回 `7056`。
- 进行中补考或相同幂等令牌恢复原尝试，避免并发再开一条。
- 通用 `start` 以 `attemptType=RETAKE` 调用时走同一护栏，且不得新建考生。

## 验证结果

- TDD Red：范围、窗口、次数、`ENDED` 可补考和进行中恢复均先失败（落到 6503/7006 或误开新尝试）。
- Green：`ExamAttemptServiceImplTest` 17/17、`ExamAttemptListControllerTest` 3/3。
- 回归：`ExamLearnerServiceImplTest` 4/4、`ExamServiceImplTest` 5/5。
- 命令：
  `mvn -pl sts-service-admin -am test -Dtest=ExamAttemptServiceImplTest,ExamAttemptListControllerTest,ExamLearnerServiceImplTest,ExamServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
  ，29/29 通过。
- OpenAPI JSON 可解析；`git diff --check` 通过。
- 未运行项：无 schema/SQL 变化，未跑 integration-test。

## Review

- 规格符合性：通过。接口仍只有考生 ID 与幂等令牌；窗口、范围、次数来自既有策略字段；新增 7054/7055/7056。
- 代码质量：通过。校验集中在 Service，无无关重构，无前端改动。
