# Design：学员培训任务按计划状态可见

## 1. 结论

学员读接口在「当前用户是该参训人」之外，必须校验培训计划 `publishStatus`。可见集合固定为 `PUBLISHED`、`RUNNING`、`ENDED`。
`DRAFT`、`CANCELLED`、`ARCHIVED` 对学员不可见。这与考试学员任务、草稿不得进入执行链，以及 `enter` 仅允许已发布/进行中的规则对齐。

人工已确认（2026-09-07）：`CANCELLED` 默认不进入学员任务。

## 2. 可见性

| 状态          | 列表  | 详情           | enter            |
|-------------|-----|--------------|------------------|
| `DRAFT`     | 不返回 | 404 / `8155` | 409 / `8154`（保持） |
| `PUBLISHED` | 返回  | 200          | 允许               |
| `RUNNING`   | 返回  | 200          | 允许               |
| `ENDED`     | 返回  | 200          | 409 / `8154`（保持） |
| `CANCELLED` | 不返回 | 404 / `8155` | 409 / `8154`（保持） |
| `ARCHIVED`  | 不返回 | 404 / `8155` | 409 / `8154`（保持） |

他人参训记录仍为 403 / `8150`，先于计划状态判断。

`8155` 使用 HTTP 404，避免把未对学员开放的草稿/取消/归档计划详情返回给学员端。

## 3. 契约

- `GET /api/training/learner/tasks`：只返回当前用户、且计划状态属于可见集合的任务；每条含 `publishStatus`。
- `GET /api/training/learner/participants/{trainingParticipantId}`：本人且可见才返回；否则 `8150` 或 `8155`。
- `TrainingLearnerTaskVO.publishStatus`：`PUBLISHED` / `RUNNING` / `ENDED`。
- 权限仍为 `training:learner:task`。不新增路径。

`enter` 不改允许集合：仅 `PUBLISHED`/`RUNNING`。前端用列表上的 `publishStatus` 区分学习入口与历史只读。

## 4. 实现

在 `TrainingWorkflowServiceImpl` 集中判断可见状态：

1. `listLearnerTasks` 查出本人参训记录后，丢弃计划不可见的项，并写入 `publishStatus`。
2. `getLearnerParticipant` 在本人校验通过后，计划不可见则 `8155`。
3. 不改 Mapper SQL、不改表结构。

## 5. 风险

- 语义不兼容：依赖草稿任务出现的调用方会看到空列表。这是缺陷修复，不是新业务能力。
- `ENDED` 出现在列表但不能 `enter`。必须返回 `publishStatus`，否则前端只能误点后再吃 `8154`。

## 6. 前后端影响

仅后端；前端以 OpenAPI 为准另开。
