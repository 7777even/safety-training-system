# 培训计划综合成绩计算与确认 QA

## 范围

PC 培训计划工作台的参训人结果抽屉：复用既有综合成绩计算、确认和批次查询接口，不新增后端契约、路由或菜单。

## 自动化验证

在 `frontend/` 执行：

```text
pnpm --filter pc-web exec node --test tests/evaluation-result-api.test.mjs tests/training-plan-api.test.mjs tests/training-workspace-ui.test.mjs
```

结果：23 项通过，0 项失败。覆盖计算/确认/查询接口映射、当前计划与参训人上下文、综合表单绑定、`RUNNING` 门禁、权限入口和评价表单提示。

```text
pnpm --filter pc-web typecheck
pnpm --filter pc-web exec eslint src/api/evaluation-result.ts src/views/admin/business-center/training/plan/workspace/index.vue src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue --max-warnings=0
```

结果：通过。

`openspec validate 2026-09-15-add-plan-composite-calculation --strict` 与 `git diff --check` 均通过。

## 待人工验收

使用拥有 `evaluation:calculation:calculate`、`evaluation:calculation:confirm`、`evaluation:calculation:list` 的管理员，在 `RUNNING` 混合计划中：

1. 打开参训人结果，确认表单版本和参训人不需要手工输入。
2. 计算后确认出现待确认批次，正式综合成绩不提前变更。
3. 确认最新批次后，核对综合成绩、正式批次 ID 和历史状态。
4. 结束计划，核对计划进入 `ENDED` 并冻结结果。
5. 分别使用缺少计算、确认、查看权限的账号，确认仅隐藏对应操作且后端仍返回真实鉴权结果。
