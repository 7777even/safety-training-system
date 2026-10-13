# Tasks：培训标准基础规则唯一性与学员详情契约修正

> 状态：已于 2026-09-07 确认并完成 Task 1～2。Owner：lims。

## Task 1：完成后端对培训标准基础规则唯一性的强制校验

- **状态**：已完成（2026-09-07）
- **Owner**：lims
- **输入**：本 Change 的 proposal、design、spec；现行 `TrainingStandardServiceImpl.replaceRules` / `publish`；功能项 5、34。
- **输出**：替换规则和发布标准时的 `TOTAL_HOURS` / `ONLINE_RATIO` 唯一性校验、业务码 `8016`、同步后的 OpenAPI 与零 DB 测试。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持既有替换规则、发布标准路径与权限；新增 `8016`。
- **数据影响**：无。不改表结构，不新增 migration。
- **验收标准**：
  1. 请求含两条 `TOTAL_HOURS` 或两条 `ONLINE_RATIO` 时，替换规则返回 409/`8016`，不修改既有规则和版本。
  2. 各一条基础规则加多条指定资源规则时替换成功。
  3. 空规则列表仍表示清空，不因缺少基础规则失败。
  4. 草稿标准库中已有重复基础规则时，发布返回 409/`8016` 并保持草稿。
  5. `docs/api/training.openapi.json` 登记 `8016` 及替换/发布接口说明。
- **测试方式**：
  1. 按 Red → Green → Refactor，先扩展 `TrainingStandardServiceImplTest` 并观察失败。
  2. 在 `backend/` 运行受影响零 DB 测试。本 Task 不改 Entity/Mapper/SQL，不跑 IT。

## Task 2：完成学员参训详情 OpenAPI 与真实对象对齐

- **状态**：已完成（2026-09-07）
- **Owner**：lims
- **输入**：本 Change 的 proposal、design、spec；现行 `TrainingLearnerParticipantDetailVO` 与 `getLearnerParticipant`；功能项
  48、56。
- **输出**：`training.openapi.json` 中学员详情对象 schema、`publishStatus` 必返、有效 example；必要时补零 DB 断言。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持 `GET /api/training/learner/participants/{trainingParticipantId}` 路径与权限；不改响应 JSON 形状。
- **数据影响**：无。
- **验收标准**：
  1. `participant` 引用 `TrainingParticipantVO`，`plan` 引用 `TrainingPlanVO`。
  2. `TrainingPlanVO.publishStatus` 为必返字段。
  3. 成功响应 example 为嵌套对象，且 `plan.publishStatus` 为 `PUBLISHED`、`RUNNING` 或 `ENDED`。
  4. 学员详情零 DB 断言 `participant`、`plan` 为对象且 `publishStatus` 有值。
- **测试方式**：
  1. 扩展或核对 `TrainingWorkflowServiceImplTest#getLearnerParticipant_allowsEndedPlan`。
  2. 用 JSON 解析校验 OpenAPI schema、字段注释和 example。
  3. 完成双轴 Review 后更新本文件、进度表、QA / Retro。

## 完成门槛

1. 规格符合性 Review：通过。唯一性入口、错误码与学员详情对象契约与本 Change 一致。
2. 代码质量 Review：通过。无表结构/权限变更；校验发生在写库之前；未改前端。
