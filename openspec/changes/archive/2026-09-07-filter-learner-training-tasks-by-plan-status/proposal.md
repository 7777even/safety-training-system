# Proposal：学员培训任务按计划状态可见

## Why

`GET /api/training/learner/tasks` 只按当前登录人是否被指派查询，不看培训计划 `publishStatus`
。草稿阶段就会指派参训人，未发布计划因此进入学员任务；进入接口却只允许 `PUBLISHED`/`RUNNING`。前端按此反馈，且已确认取消态默认不给学员看。

## What Changes

- 学员任务列表只返回计划状态为 `PUBLISHED`、`RUNNING`、`ENDED` 的本人参训记录。
- 学员参训详情使用同一可见性；`DRAFT`、`CANCELLED`、`ARCHIVED` 返回 404 / `8155`。
- 任务 VO 增加 `publishStatus`，便于区分可进入与只读历史。
- `enter` 仍仅允许 `PUBLISHED`/`RUNNING`；`ENDED` 可看详情，进入继续 `8154`。
- **BREAKING（语义）**：原先会出现在列表中的草稿、取消、归档计划不再返回。

## Capabilities

### New Capabilities

- `learner-training-task-visibility`：学员培训任务与详情的计划状态可见性。

### Modified Capabilities

- `training-execution-loop`：学员任务/详情在身份隔离之外，增加计划发布状态门禁。

## Impact

- `TrainingWorkflowServiceImpl`、`TrainingLearnerTaskVO`、`docs/api/training.openapi.json`。
- 不改数据库、权限码、`enter` 允许进入的状态。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- **功能项 48**：展示学员培训学习列表，按培训计划状态分类后学习。
- **功能项 56**：我的培训展示学员培训任务及结果。

## 目标

1. 未发布、已取消、已归档计划不出现在学员任务中。
2. 已发布、进行中、已结束计划对本人参训人可见；已结束用于查看历史结果。
3. 任务返回计划 `publishStatus`。
4. 详情对不可见计划不泄露编排内容。

## 非目标范围

- 不改前端、Mock、联调。
- 不把 `CANCELLED` 纳入默认学员任务；取消态历史另开 Change。
- 不改 `enter`、执行生成、教师任务、管理端计划列表。
- 不按计划 `status` 启停用另做学员裁剪。
