# QA：M2 前端 Task 2 试卷组卷、预览与版本发布

日期：2026-07-28
Change：`2026-07-27-implement-m2-frontend-question-exam` Task 2

## 验证结果

- TDD：先观察 Mock 开关和独立组卷、版本页面缺失的失败测试；实现后 Node 定向测试 7/7 通过。
- Contract：覆盖试卷 CRUD、组卷、预览、发布、版本详情；默认使用会话内 Mock，`VITE_USE_API_MOCK=false` 时改为真实 OpenAPI 请求。
- 交互：组卷方式读取既有试卷的 FIXED/RANDOM/CUSTOM 类型；随机规则提交 `drawCount`、`sortOrder`；发布按钮在预览成功前拦截；版本页仅展示快照。
- 质量：定向 `eslint --max-warnings=0`、`pnpm typecheck`、`pnpm build`、`git diff --check` 均通过。

## 未覆盖项

真实后端尚未可联调；鉴权、服务端快照、预览返回和 HTTP 4xx 外壳留待 M2 Task 6 以真实接口验证，Mock 不作为真实闭环依据。
