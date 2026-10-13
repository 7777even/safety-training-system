# QA：移动端正式考试考前须知与单向开考

日期：2026-08-28  
OpenSpec：`2026-08-28-add-mobile-exam-notice-before-start` Task 1～2

## 验收结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 开考前须知 | 通过 | `START` 任务仅进入 `pages/exam/notice`；须知加载和返回不调用开考接口。 |
| 显式开考与单向路由 | 通过 | 须知页点击“开始答题”才调用既有 `/api/exam/attempts/start`，并以 `redirectTo` 进入作答页。 |
| 其他任务分流 | 通过 | `CONTINUE`、`VIEW_RESULT`、`WAIT_REVIEW` 保持既有入口，不经过须知页。 |
| 详情与权限 | 通过 | 须知复用既有 `/api/exam/exams/{examId}`；开发人员已确认学员角色具备 `exam:exam:list`。 |
| Mock 与总分展示 | 通过 | Mock 使用 Contract 的小数及格比例 `0.6`；总分按 `passScore / passRatio` 反推并保留两位小数。 |
| 定向自动化测试 | 通过 | `node --test frontend/mobile-app/tests/learner-course-learning.test.mjs frontend/mobile-app/tests/learner-exam-pages.test.mjs`，20/20。 |
| 类型、Lint 与构建 | 通过 | `pnpm --filter @safety-training/mobile-app typecheck`；受影响生产源码 ESLint；`pnpm --filter @safety-training/mobile-app run build:h5`。 |
| 双轴 Review | 通过 | 规格符合性与代码质量复核均通过；权限前置条件已由开发人员确认。 |

## 后续验证

- 待非生产环境使用学员账号验证须知详情读取和首次开考跳转；该项不阻塞当前代码验收。
