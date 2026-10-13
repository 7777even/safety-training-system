# Tasks：监管个人培训记录下钻

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成监管培训记录只读下钻

- [x] 1.1 完成监管培训记录只读下钻

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1～D3。
- **输出**：四个 GET；OpenAPI；未切换 9860。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：`/regulation/training-plans` 及参训人/结果。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 已切换视角时返回计划/参训人/结果。
  2. 未切换 400/9860。
  3. 非 REGULATOR 403/4065。
- **测试方式**：Red → Green `RegulationTrainingRecordServiceImplTest`、`RegulationControllerTest`。
- **Owner**：lims
- **输入**：design D1～D3。
- **输出**：四个 GET；OpenAPI；未切换 9860。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：`/regulation/training-plans` 及参训人/结果。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 已切换视角时返回计划/参训人/结果。
  2. 未切换 400/9860。
  3. 非 REGULATOR 403/4065。
- **测试方式**：Red → Green `RegulationTrainingRecordServiceImplTest`、`RegulationControllerTest`。
