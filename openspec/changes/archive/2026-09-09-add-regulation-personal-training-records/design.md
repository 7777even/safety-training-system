# Design：监管个人培训记录下钻

## Context

`GET /regulation/overview` 只给企业计划数和开放预警数。`X-Target-Tenant-Id` 会把租户切到目标企业，但监管角色没有
`training:plan:list`。培训结果 GET 已含 `sourceRecordType` / `sourceRecordId` / `finalAttemptId`。

## Goals / Non-Goals

**Goals:**

- 监管只读：计划、参训人、逐内容结果（学时、成绩、完成时间、来源标识）。
- 未切换视角不得读。

**Non-Goals:**

- 给监管开通培训写权限。
- 新投影表。
- 签到明细。

## Decisions

### D1：监管专用 GET，委托培训查询

`RegulationTrainingRecordService` 先 `requireRegulator()`，再校验当前租户 ≠ 登录监管租户（说明已切换视角），否则 400 /
`9860`。然后委托：

- `TrainingPlanService.page` / `get`
- `TrainingPlanService.listParticipants`
- `TrainingResultSyncService.getResults`

SQL 仍走当前租户插件。

### D2：接口

权限一律 `regulation:overview:view`。

- `GET /regulation/training-plans`
- `GET /regulation/training-plans/{trainingPlanId}`
- `GET /regulation/training-plans/{trainingPlanId}/participants`
- `GET /regulation/training-plans/{trainingPlanId}/participants/{trainingParticipantId}/results`

请求头：`X-Target-Tenant-Id`（企业租户 ID）。

### D3：复用 VO

`TrainingPlanVO`、`TrainingParticipantVO`、`TrainingParticipantResultsVO`。不发明第二套字段。

## Risks

- 未带头时会读监管租户空数据；用 9860 挡住。
- 结果项来源是考试答卷/线下成绩，不是签到。
