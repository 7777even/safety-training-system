# Proposal：学员只读本人线下场次

学员进入线下内容后没有可用详情：管理端场次 GET 要 `training:plan:list`，且不含本人出勤和成绩；enter 只返回场次定位字段，结束计划还不能
enter。超管联调会把管理接口测通，真正学员会 403。

## Why

线下页需要本人可见的场次通知、出勤、成绩和获得学时。这些不能靠管理端场次接口或把 enter 当详情。权限只表示有没有钥匙，不能把管理员场次查询发给学员。

## What Changes

- 新增 `GET /api/training/learner/offline-sessions/{offlineSessionId}`，权限 `training:learner:task`。
- 按登录用户定位本人参训记录；计划须为 RUNNING 或 ENDED。
- 只返回该场次公开信息与本人出勤、已评分成绩、投影结果。
- enter 仍只返回跳转用 `offlineSessionId`。不开放管理端场次接口给学员。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：学员可用本人权限读取自己的线下场次详情，不得调用管理端场次接口。

## Impact

- 后端：`TrainingWorkflowController` / Service；零 DB。
- 契约：`docs/api/training.openapi.json` 新路径。无 DDL、无新权限码。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 功能项 37：线下培训结果对学员可见。
- 功能项 48、56：学员培训任务可进入并查看线下内容。

## 目标

1. 学员用 `training:learner:task` 能读到本人场次、出勤和成绩。
2. 非参训或计划不可见时 404/8155。
3. 管理端场次接口语义不变。

## 非目标范围

- 不改管理端场次 CRUD、不授 `training:plan:list` 给学员。
- 不做应到名单、场次状态机、学员自助签到、多成绩项收口。
- 不改前端。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
