# Tasks：学员只读本人线下场次

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成学员只读线下场次详情

- [x] 1.1 完成学员只读线下场次详情

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1～D3。
- **输出**：`GET /api/training/learner/offline-sessions/{offlineSessionId}`；OpenAPI；enter 说明指向该 GET。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：新路径；权限 `training:learner:task`；错误码 8155。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 本人 RUNNING 可读场次、出勤、成绩、投影。
  2. 未参训、场次不存在、计划非 RUNNING/ENDED 均为 404/8155。
  3. ENDED 可读；enter 仍不增肥。
  4. 不调用、不改管理端场次 GET 权限。
- **测试方式**：`TrainingWorkflowServiceImplTest`、`TrainingWorkflowControllerTest`；`git diff --check`。

## 完成门槛

1. 规格符合性：登录态派生参训人，不授管理端权限。
2. 代码质量：无前端改动，无 DDL。
