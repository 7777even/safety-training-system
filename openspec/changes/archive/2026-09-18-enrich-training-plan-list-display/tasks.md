# Tasks：补充培训计划列表信息展示

## T1 列表安排信息展示 [x]

- **Owner**：前端
- **输入**：`TrainingPlanVO.startTime`、`endTime`、`location`、`createTime`
- **输出**：管理端列表的培训时间、培训地点、创建时间列
- **前端影响**：培训计划列表页面及定向测试
- **API 依赖**：既有 `GET /api/training/plans`
- **数据影响**：无
- **验收**：时间精确到分钟；缺失地点或时间显示 `-`；不显示负责人内部 ID
- **测试**：PC 定向 Node 测试、受影响文件 ESLint、类型检查

## 验证记录

- `pnpm --dir pc-web exec node --test tests/training-plan-api.test.mjs`：6/6 通过。
- `pnpm --dir pc-web exec eslint src/api/training-plan.ts src/views/admin/business-center/training/plan/index.vue`：通过。
- `pnpm --dir pc-web typecheck`：通过。
