# QA：培训计划结束前检查（PC）

## 范围

- OpenSpec：`2026-09-15-guide-training-plan-end-precheck`。
- 影响页面：培训管理 → 培训计划 → 人员与执行 → 发布与执行。
- 接口：只读取既有计划执行链接、线下场次和逐人培训结果；未新增接口或字段。

## 自动化验证

| 检查 | 结果 |
| --- | --- |
| `pnpm --dir frontend/pc-web exec node --test tests/training-workspace-ui.test.mjs` | 17/17 通过 |
| `pnpm --dir frontend/pc-web run typecheck` | 通过 |
| `pnpm --dir frontend/pc-web exec eslint src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue src/views/admin/business-center/training/plan/workspace/index.vue` | 通过 |
| `git diff --check` | 通过 |

## 覆盖的场景

1. `IN_PROGRESS` 线下场次显示名称和教师端结束指引，并禁用结束按钮。
2. 进行中的课程学习或考试作答、未安排内容、未进入 `COMPLETED` / `FAILED` / `EXPIRED` 的必修内容、未确认正式综合成绩分别显示可读阻塞项。
3. 检查只读取状态和 `officialCalculationRunId`，不以分数是否为 `0` 判断完成。
4. 取消计划在独立风险区展示，仍经过既有二次确认；后端结束门禁仍保留。

## 真实环境待验收

需先部署本次 PC 包。以一个包含线下场次、必修内容及综合评价表单的 `RUNNING` 计划验证：结束线下场次、完成必修内容、确认综合成绩后点击“刷新检查”，再结束计划并核对 `ENDED` 和冻结结果。
