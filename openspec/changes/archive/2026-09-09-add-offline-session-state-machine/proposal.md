# Proposal：线下场次受控状态机

生成场次后状态为 `SCHEDULED`，但普通更新可任意写 `sessionStatus`，没有开始、结束、取消。教师录分与场次状态没有受控边界。

## Why

场次状态是执行边界。可被 PUT 改成任意值时，教师动作、录分和计划结束门禁都无法依赖它。P1 要求「已安排 → 进行中 → 已结束/已取消」。

## What Changes

- 状态只允许：`DRAFT`、`SCHEDULED`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`。
- 受控动作：`start` / `complete` / `cancel`。PUT 与创建不得改状态。
- 手工创建固定 `DRAFT`；计划生成仍写 `SCHEDULED`。
- 仅 `IN_PROGRESS` 可录分。
- 教师任务 `availableActions` 按状态给出 `START` / `RECORD_SCORE` / `COMPLETE` / `CANCEL`。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：线下场次受控状态机。

## Impact

- 后端：场次 Service、录分门禁、教师动作、ExecutionController。
- 契约：新增 start/complete/cancel；PUT 去掉 `sessionStatus`。
- 无 DDL。
- 仅后端；前端以 OpenAPI 为准另开。

## 非目标

签到、Excel 导入、监管下钻、交卷空白答案、前端。
