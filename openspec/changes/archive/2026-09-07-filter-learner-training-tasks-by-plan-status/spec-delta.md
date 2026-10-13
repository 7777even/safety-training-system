# Spec Delta：学员培训任务按计划状态可见

## 变更类型

- 新增能力规格：`learner-training-task-visibility`
- 修改既有能力：`training-execution-loop`（学员任务/详情增加计划状态门禁）
- 不删除或重命名既有 Requirement。

## 机器可读 Delta

正式 Requirements 与 Scenarios 位于：

- `specs/learner-training-task-visibility/spec.md`

该 Delta 新增以下要求：

1. 学员任务列表只返回 `PUBLISHED` / `RUNNING` / `ENDED` 的本人参训计划。
2. `DRAFT` / `CANCELLED` / `ARCHIVED` 不进列表，详情返回 404 / `8155`。
3. 任务 VO 返回计划 `publishStatus`；`ENDED` 可看不可进入。
