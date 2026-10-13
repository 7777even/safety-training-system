# QA：补充培训计划列表信息展示

## 范围

管理端培训计划列表复用既有 `GET /api/training/plans` 响应，展示培训时间、培训地点与创建时间。

## 自动化验证

- `pnpm --dir pc-web exec node --test tests/training-plan-api.test.mjs`：6/6 通过；覆盖新列、时间与地点格式化、未展示负责人内部 ID。
- `pnpm --dir pc-web exec eslint src/api/training-plan.ts src/views/admin/business-center/training/plan/index.vue`：通过。
- `pnpm --dir pc-web typecheck`：通过。

## 人工验收待办

真实登录态下查询含完整时间、地点的计划，以及地点为空或时间缺失的历史计划，确认列表分别显示格式化值和 `-`。本次未进行浏览器真实接口验收，不将自动化验证等同于真实联调。
