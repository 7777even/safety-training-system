# QA：学员培训任务按计划状态可见

## 范围

OpenSpec Change `2026-09-07-filter-learner-training-tasks-by-plan-status` Task 1。仅后端与
`docs/api/training.openapi.json`；前端以 OpenAPI 为准另开。

## 验收口径

- 学员任务只返回本人参训、且计划 `publishStatus` 为 `PUBLISHED` / `RUNNING` / `ENDED` 的记录。
- `DRAFT` / `CANCELLED` / `ARCHIVED` 不进列表；详情返回 404 / `8155`。
- 任务含 `publishStatus`。`ENDED` 可看详情，`enter` 仍为 409 / `8154`。
- 他人参训记录仍为 403 / `8150`，先于计划状态判断。

## TDD 与实现证据

1. 先扩展 `TrainingWorkflowServiceImplTest`：列表应只含三态、详情拒绝草稿/取消、VO 需要 `publishStatus`。因过滤与 `8155`
   尚未实现而失败（列表仍返回 6 条；详情走到结果同步 NPE）。
2. 最小实现可见性判断、任务 `publishStatus` 和详情 `8155` 后，定向零 DB 通过。
3. 补他人记录先于计划状态返回 `8150` 的用例，锁定不得用 `8155` 掩盖身份错误。

## 验证结果

- 受影响零 DB：
  `mvn -pl sts-service-admin -am test -Dtest=TrainingWorkflowServiceImplTest,TrainingWorkflowControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
  → 18 项，0 失败，0 错误，0 跳过。
- 带 DB IT：未运行。本 Task 不改 Entity、Mapper、SQL、schema 或持久化事务。
- Admin 全量零 DB：未运行，以受影响测试为准。
- `docs/api/training.openapi.json`：`python3` JSON 解析通过；任务/详情 description、`publishStatus` 字段注释与 example、
  `8155` 已登记。
- `git diff --check`：通过。

## Review

- 规格符合性：通过。可见集合与人工确认一致；`CANCELLED` 默认不看；`enter` 允许状态未扩大。
- 代码质量：通过。无数据库/权限变更；未改教师任务或管理端列表；前端未改。
- 需人工决策：无。取消态历史若以后要给学员看，须另开 Change。
