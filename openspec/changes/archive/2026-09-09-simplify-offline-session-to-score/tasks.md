# Tasks：线下场次收口为录成绩

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成线下投影按成绩计算并删除签到接口

- [x] 1.1 完成线下投影按成绩计算并删除签到接口

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D4。
- **输出**：投影只读成绩；签到 HTTP 删除；OpenAPI 删除 attendance 路径。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：删除 `/training/attendance` 与 confirm。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 无分 PENDING 0 秒；合格 COMPLETED 且秒数=requiredHours×3600；不及格 FAILED 0 秒。
  2. 出勤行不影响完成。
  3. 签到 HTTP 不再存在。
- **测试方式**：Red → Green `TrainingResultSyncServiceImplTest`、`TrainingExecutionControllerTest`。

## Task 2：完成教师应到名单与一人一条成绩

- [x] 2.1 完成教师应到名单与一人一条成绩

- **状态**：已完成
- **Owner**：lims
- **输入**：design D2、D3。
- **输出**：教师任务详情名单与计数；成绩按场次+参训人更新原行。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：教师任务详情与 `POST /training/offline-scores`。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 详情返回参训人及分数，`scoredCount`/`pendingScoreCount` 正确。
  2. 不同 `scoreName` 再次提交更新同一行。
- **测试方式**：Red → Green `TrainingWorkflowServiceImplTest`、`TrainingOfflineScoreServiceImplTest`。

## Task 3：完成学员场次瘦身、契约与文档

- [x] 3.1 完成学员场次瘦身、契约与文档

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D4；功能项最小集合。
- **输出**：学员 GET 无出勤字段；OpenAPI 与架构文档、混合计划 IT。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：`GET /training/learner/offline-sessions/{id}`。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 学员 GET 返回地点、时间、成绩、投影，无出勤字段。
  2. IT 仅凭成绩可使线下项 COMPLETED。
  3. 架构文档与 OpenAPI 不再写出勤完成口径。
- **测试方式**：`TrainingWorkflowServiceImplTest`、`TrainingMixedPlanLifecycleIT`；`git diff --check`。

## 完成门槛

1. 规格符合性：完成只认成绩；签到 HTTP 删除；表保留。
2. 代码质量：无前端改动。
