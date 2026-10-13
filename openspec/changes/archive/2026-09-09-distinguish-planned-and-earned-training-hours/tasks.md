# Tasks：区分培训安排学时与获得学时

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成学时口径契约收口

- [x] 1.1 完成学时口径契约收口

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1～D3。
- **输出**：training OpenAPI 区分安排学时与获得学时；发布/结束/结果说明对齐；发布校验与投影代码注释；架构说明一句。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新路径，仅改 description。
- **数据影响**：无 DDL，不改计算公式。
- **验收标准**：
  1. `requiredHours` 标明安排学时，不与获得学时混用。
  2. `effectiveDurationSeconds`/`onlineHours`/`offlineHours` 标明获得学时及考试/题库为 0。
  3. 题库 `durationSeconds` 标明作答时限。
  4. 既有学时零 DB 仍通过，证明公式未改。
- **测试方式**：回归 `TrainingResultSyncServiceImplTest`、`TrainingPlanPublishValidatorTest`；`git diff --check`。

## 完成门槛

1. 规格符合性：只收口口径，不改学时公式。
2. 代码质量：无前端改动，无 DDL。
