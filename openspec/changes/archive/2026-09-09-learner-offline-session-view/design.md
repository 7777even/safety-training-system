# Design：学员只读本人线下场次

## Context

课程/考试是 enter 拿 ID 再 GET 资源。线下只有 POST enter（名称、地点、起止；须 RUNNING），没有学员 GET。
`GET /training/offline-sessions/{id}` 是 `training:plan:list`，按 ID 直读编排字段，不含本人出勤/成绩。

## Goals / Non-Goals

**Goals:**

- 学员只读 GET，登录态定位人。
- RUNNING/ENDED 可看；enter 保持跳转。
- 不可见统一 404/8155。

**Non-Goals:**

- 管理端场次接口改权限或按角色分支。
- 应到名单、状态机、自助签到、多成绩项。
- 前端页面。

## Decisions

### D1：新路径，不复用管理端 GET

`GET /api/training/learner/offline-sessions/{offlineSessionId}` 挂 `TrainingWorkflowController`，`training:learner:task`
。不在管理端 `getSession` 里按角色返回不同 VO。

### D2：登录用户派生参训人

场次 → 计划须 RUNNING/ENDED → `userId=当前登录` 的参训记录。路径不接 `userId`/`trainingParticipantId`。超管未参训同样
8155。

不存在、未参训、计划不可见都 8155，不回 8201/8101 以免枚举。

### D3：返回本人切片，enter 不增肥

场次：名称、地点、起止、签到窗口、`attendanceRule`、`sessionStatus`、`planContentId`、`trainingParticipantId`。

出勤：本人记录；无则空，`confirmed=false`。成绩：已 `scoredBy` 的一项（现有 LIMIT 1）。投影：`resultStatus`、
`effectiveDurationSeconds`。

不含教师用户 ID、版本、他人名单、`confirmedBy`。

enter 仍只给 `offlineSessionId`（及现有名称/地点）。ENDED 仍 8154；历史看 GET。

## Risks / Trade-offs

- 多成绩项 LIMIT 1 与投影一致，另开 Change。
- 无出勤行时页面显示未签到，等教师名单 Change。

## Migration Plan

无 DDL。部署后前端改调新 GET。回滚即下线路由。

## Open Questions

无。
