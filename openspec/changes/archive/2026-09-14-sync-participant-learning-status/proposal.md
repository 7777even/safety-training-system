# Proposal：同步参训人学习中状态

> 状态：已获人工确认。Owner：Codex。

## Why

培训内容投影已进入个人执行态时，`training_participant.participant_status` 仍停留在 `PENDING`，导致双端培训列表错误显示“待开始”。

## What Changes

- 在未冻结参训人的内容投影同步后，任一内容不再为 `PENDING` 时将参训状态从 `PENDING` 推进为 `LEARNING`。
- 不改变计划状态、内容项状态、最终 PASSED/FAILED 结算、接口字段或数据库结构。
