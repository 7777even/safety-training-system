# QA：培训模板与评价表单 OpenAPI 补全

日期：2026-07-30  
Owner：lims

## 范围

- `training.openapi.json`
  - `GET/PUT/DELETE /api/training/template-categories/{templateCategoryId}`
  - `GET/PUT/DELETE /api/training/templates/{trainingTemplateId}`
- `evaluation-form.openapi.json`
  - `GET/PUT/DELETE /api/evaluation/forms/{evaluationFormId}`
- 相关创建/修改请求字段、中文注释和 example。

## 验证

- 三份 OpenAPI 均通过 `jq empty`。
- 9 个 operation 的 Method 完整性检查通过。
- 新增 components property 中文 `description` 检查通过。
- operation 的 `summary`、`description`、Controller tag 和 `x-permission` 与现有 Controller 对齐。
- `git diff --check` 通过。

