# Tasks：补齐考试作答、批阅与成绩管理入口

> 状态：Task 1 已完成。

## Task 1：在考试工作台增加考试情况阶段

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：既有考试工作台、`GET /api/exam/attempts` Contract、既有批阅与成绩处置页面。
- 输出：按当前考试筛选的作答记录 Panel，以及查看作答、批阅和成绩处置入口。
- 前后端影响：仅 PC Web；不修改 API、权限、数据库或路由契约。
- API 依赖：`GET /api/exam/attempts`、既有作答快照/批阅/成绩发布接口。
- 数据影响：无。
- 验收标准：能够在一场考试内查看全部有权可见的学员作答，按状态筛选，进入既有批阅和成绩处置；未具备权限时不显示相应操作入口。
- 测试方式：先新增失败的页面结构和查询参数回归用例；再运行定向 Node 测试、PC Web typecheck、受影响 lint 与 `git diff --check`。

修正记录：成绩处置路由以 `examAttemptId` 即可唯一定位作答；此前工作台和跨考试成绩列表额外携带 `examCandidateId`，而教师批阅入口未携带，导致同一页面出现两种 URL，且无参数时补考/练习重置不可用。现统一由成绩处置页加载作答后读取其 `examCandidateId`，所有入口仅保留路径参数。新增回归用例先失败后通过；定向测试 2/2、typecheck、受影响 lint、PC build 与 `git diff --check` 通过。
