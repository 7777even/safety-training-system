# QA：M2 前端 Task 1 题库资源、题目编辑页与版本管理

日期：2026-07-28
Change：`2026-07-27-implement-m2-frontend-question-exam` Task 1

## 验证结果

- TDD：先新增 Mock 开关与独立页面的失败测试，确认 API 未提供开关且页面文件不存在；最小实现后转绿。
- 定向测试：
  `node --test tests/question-bank-api.test.mjs tests/question-bank-management.test.mjs tests/question-editor-pages.test.mjs tests/question-mock.test.mjs`
  ，10/10 通过。
- PC：定向 `eslint`、`pnpm typecheck`、`pnpm build` 通过。
- 文档与差异：`git diff --check` 通过。

## 覆盖范围

- 默认 Contract Mock 与 `VITE_USE_API_MOCK=false` 真实请求切换。
- 题库、知识点、分类、题目分页、创建、详情、版本发布、素材关联和 JSON 导入批次统计。
- 题目新建与详情为独立页面；发布后只读。
- Mock 覆盖发布版本不可编辑、重复编码导入失败统计及 Contract 筛选字段。
- 题库、知识点树和题库分类采用左侧一级选择、右侧对应数据的主从布局；题库默认选择首项。
- 新增题库可选关联题库分类和知识点，分类右侧按关联结果展示所属题库。

## 未覆盖项

- 真实后端联调环境不可用，鉴权、跨域、服务端分页、权限拒绝及真实业务错误码留待 Task 6，在 `VITE_USE_API_MOCK=false` 下验收。
