# Tasks：教师任务返回场次乐观锁 version

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成教师任务读侧 version

- [x] 1.1 完成教师任务读侧 version

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D2、D3。
- **输出**：列表与详情 VO 返回场次 `version`；OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：`GET /training/teacher-tasks`、`GET /training/teacher-tasks/{id}`；
  `POST /training/offline-sessions/{id}/start|complete|cancel`。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 教师任务列表返回场次 `version`。
  2. 教师任务详情返回场次 `version`。
  3. OpenAPI schema 与 example 含 `version`，说明供 start/complete/cancel 回传。
- **测试方式**：Red → Green `TrainingTeacherTaskServiceImplTest`、`TrainingWorkflowServiceImplTest`。
