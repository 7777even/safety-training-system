# Tasks：学员培训任务按计划状态可见

> 状态：已于 2026-09-07 获得人工确认并完成 Task 1。Owner：lims。

## Task 1：完成学员培训任务与详情的计划状态门禁

- **状态**：已完成（2026-09-07）
- **Owner**：lims
- **输入**：本 Change 的 proposal、design、spec delta；现行 `TrainingWorkflowServiceImpl` 学员读接口；
  `docs/api/training.openapi.json`；功能项 48、56。
- **输出**：按计划状态过滤的学员任务与详情、任务 VO 的 `publishStatus`、同步后的 OpenAPI、零 DB 测试、QA / Retro 与进度证据。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持 `GET /api/training/learner/tasks`、`GET /api/training/learner/participants/{trainingParticipantId}`
  路径与权限；更新契约说明、`TrainingLearnerTaskVO.publishStatus` 与 `8155`。
- **数据影响**：无。不改表结构，不新增 migration。
- **验收标准**：
  1. 本人参训的 `PUBLISHED`、`RUNNING`、`ENDED` 计划出现在学员任务中，且 `publishStatus` 与计划一致。
  2. 本人参训的 `DRAFT`、`CANCELLED`、`ARCHIVED` 计划不出现在学员任务中。
  3. 对不可见计划查询参训详情返回 HTTP 404 / `8155`，不返回计划内容。
  4. 对 `ENDED` 计划查询详情成功；`enter` 仍返回 409 / `8154`。
  5. 他人参训记录仍返回 403 / `8150`。
  6. `docs/api/training.openapi.json` 同步可见性规则、字段注释、example 与错误码。
- **测试方式**：
  1. 按 Red → Green → Refactor，先扩展 `TrainingWorkflowServiceImplTest` 并观察失败。
  2. 在 `backend/` 运行受影响零 DB 测试。本 Task 不改 Entity/Mapper/SQL，不跑 IT。
  3. 校验 OpenAPI JSON 结构、字段说明、example、权限和错误码。
  4. 完成规格符合性与代码质量双轴 Review；通过后更新本 Task、进度表、QA / Retro。
