# Tasks：考试与试卷工作台 Panel 收敛

> 状态：Task 1 已完成。

## Task 1：提取考试与试卷工作台阶段 Panel

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：课程、考试和试卷工作台现有实现及当前 OpenAPI。
- 输出：考试三个阶段 Panel、试卷三个阶段 Panel，以及统一使用 `admin-detail-page__surface` 的工作台根组件。
- 前后端影响：仅 PC Web；不修改接口、权限、数据或路由。
- API 依赖：既有考试、试卷、题库和素材接口。
- 数据影响：无。
- 验收标准：两个工作台的阶段内容均不再内联在根工作台；所有 Panel 位于各自 `components` 目录；功能、请求字段和交互结果保持不变；根页面在 `main.admin-detail-page__surface` 中编排 Panel。
- 测试方式：先增加失败的结构与行为回归用例；最小实现转绿；PC Web typecheck、受影响 lint 与 `git diff --check`。
