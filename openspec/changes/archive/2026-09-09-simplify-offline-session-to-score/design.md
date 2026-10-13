# Design：线下场次收口为录成绩

## Context

功能项最小：地点 + 成绩。用户确认：原出勤口径去掉；表留；签到接口删。

## Goals / Non-Goals

**Goals:** 投影只读成绩；教师名单；一人一行；删签到 HTTP；学员 GET 瘦身。

**Non-Goals:** 删表、签到、导入、图表、前端。

## Decisions

### D1：投影只读成绩

无分 `PENDING` 0 秒。有分无及格线或 ≥ `passScore`：`COMPLETED`，秒数 = `requiredHours`×3600。低于及格线：`FAILED` 0 秒。不读出勤。

### D2：应到名单 = 计划参训人

教师详情/列表计数改为应到、已评分、未评分。动作仅 `RECORD_SCORE`。

### D3：一人一行，不改唯一索引

按 `(session, participant)` 查找：有则更新（含 `scoreName`），无则插入。投影取 `scoredTime` 最新一条。

### D4：删 HTTP，留表

删除 `GET/POST /training/attendance` 与 confirm。Entity/Mapper/表保留。`hasDownstreamFacts` 仍可认历史出勤行，避免误删参训人。

## Risks / Trade-offs

- [前端仍调签到] → 404；以 OpenAPI 为准另开。
- [历史多成绩] → 更新最新行。

## Migration Plan

无 DDL。部署后签到路径消失；计划级同步按成绩补投影。

## Open Questions

无。
