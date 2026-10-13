# Tasks：完成移动端课程考核闭环

> 状态：已于 2026-08-28 获得人工确认。
>
> 测试 seams：移动端课程 API 函数与课程学习/作答/结果页面交互。

## Task 1：完成移动端课程题库练习与课程测验闭环

- 状态：已完成（2026-08-28）。
- Owner：Codex
- 输入：`course.openapi.json`、PC 端课程考核页面和本 Change。
- 输出：移动端可开始、继续、交卷和回看课程题库练习及课程测验；课程结果生效后课程目录显示完成状态。
- 前后端影响：移动端、shared 与 `course.openapi.json`；后端实现不变，前端以更新后的 OpenAPI 为准。
- API 依赖：课程考核 Attempt 的开始、详情、答案暂存和提交接口。
- 数据影响：无。
- 验收标准：课程流程不调用 `/api/exam/**`；题库练习、纯客观题测验和待批阅测验按已确认状态展示；课程结果生效后刷新目录为已完成；结果页不出现独立考试人工发布文案。
- 测试方式：已先观察课程考核延期测试失败，再完成实现；`node --test frontend/mobile-app/tests/learner-course-learning.test.mjs` 15/15、移动端 typecheck、受影响源码 ESLint 与 H5 build 均通过。
- Review：规格符合性与代码质量双轴 Review 均通过；已按 Review 校准目录状态枚举、Mock 错误码和类型边界。
