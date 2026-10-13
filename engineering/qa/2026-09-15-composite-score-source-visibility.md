# 培训综合成绩来源与结果抽屉 QA

## 范围

修复综合成绩计算把空聚合写成 0 的问题；让草稿计划可配置计分资格，并在 PC 结果抽屉展示内容是否纳入和每个计算批次实际采用的来源分数。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -Dtest=CalculationRunServiceImplTest test
mvn -pl sts-service-admin -Dtest=TrainingResultSyncServiceImplTest test
```

结果：计算服务 6/6、培训结果同步 29/29 通过。覆盖计算前刷新、课程未出分而考试 98/线下 90 已出分、无来源拒绝 8805、真实 0 分来源允许计算，以及题库练习不参与成绩聚合。

在 `frontend/` 执行：

```text
pnpm --filter pc-web exec node --test tests/evaluation-result-api.test.mjs tests/training-workspace-ui.test.mjs
pnpm --filter pc-web typecheck
pnpm --filter pc-web exec eslint src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue src/views/admin/business-center/training/plan/workspace/index.vue
```

结果：Node 19/19、typecheck、目标 ESLint 通过。`openspec validate 2026-09-15-fix-composite-score-source-visibility --strict` 与 `git diff --check` 通过。

## 待真实环境验收

1. 新建草稿混合计划：课程、考试、线下默认显示“计入”，题库练习显示固定“不计分”。
2. 开始计划后仅完成线下（90）与正式考试（98），课程保持未出分；点击计算，批次来源应为“尚未产生 / 98 / 90”，综合分按表单规则非 0。
3. 三类计分来源均未产生时，计算接口应返回“暂无可计入综合成绩的正式成绩”，且不新增批次。
4. 用真实考试 0 分或线下 0 分重复第 3 步，确认允许生成批次，验证 0 分与空来源区分。
5. 当前截图所示已执行计划：先在受控数据修复流程核对并修复考试、线下内容的冻结 `isScored` 配置，再重新计算；历史全 0 批次保留且不得确认。
