# QA：保护独立考试未暂存答案

日期：2026-08-27

## 验收范围

- 独立考试加载和成功暂存后记录答案已保存基线。
- 编辑后手动提交或返回前提示“暂存并继续 / 留在当前页”。
- 取消提示或暂存失败时不提交、不返回；超时交卷保持原有逻辑。
- 暂存请求进行期间的新编辑仍被识别为未暂存。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| `pnpm --dir frontend/pc-web exec node --test --test-name-pattern "独立考试在手动提交或返回前保护未暂存答案" tests/learner-exam-center.test.mjs` | 通过，1/1 |
| `pnpm --dir frontend/pc-web typecheck` | 通过 |
| 规格符合性 Review | 通过 |
| 代码质量 Review | 通过 |

## 已知基线问题

完整运行 `tests/learner-exam-center.test.mjs` 时，其他未提交考试中心改动导致 3 条既有页面断言失败（`query.contextType`、`isCourseExamTask`、`attemptStatus`）；新增暂存保护用例单独通过，且本 Change 未修改这些断言涉及的任务中心行为。

`git diff --check` 同时报告工作区先前存在的 `attempt.vue` 样式尾随空格及考试工作台导入尾随空格；本 Change 未触碰它们。
