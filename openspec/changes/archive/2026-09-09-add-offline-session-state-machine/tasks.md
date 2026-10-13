# Tasks：线下场次受控状态机

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成场次 start/complete/cancel 与 PUT 护栏

- [x] 1.1 完成场次 start/complete/cancel 与 PUT 护栏

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D2。
- **输出**：三个受控动作；创建固定 DRAFT；PUT 不改状态；OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：`POST /training/offline-sessions/{id}/start|complete|cancel`。
- **数据影响**：无 DDL。
- **验收标准**：
  1. SCHEDULED start → IN_PROGRESS。
  2. 非法转移 409/8202。
  3. PUT 不改 sessionStatus。
  4. 非教师且无 plan:edit 返回 403/8131。
- **测试方式**：Red → Green `TrainingOfflineSessionServiceImplTest`、`TrainingExecutionControllerTest`。

## Task 2：完成录分门禁与教师动作

- [x] 2.1 完成录分门禁与教师动作

- **状态**：已完成
- **Owner**：lims
- **输入**：design D3。
- **输出**：仅 IN_PROGRESS 录分；教师 availableActions。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：教师任务详情、`POST /training/offline-scores`。
- **数据影响**：无。
- **验收标准**：
  1. SCHEDULED 录分 409/8203。
  2. IN_PROGRESS 可录分。
  3. 教师动作按状态返回。
- **测试方式**：Red → Green `TrainingOfflineScoreServiceImplTest`、`TrainingWorkflowServiceImplTest`。
