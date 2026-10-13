# QA：M1 前端 Task 1 机构协作与课程参与范围配置

日期：2026-07-24  
Change：`2026-07-24-implement-m1-frontend-foundation-course` Task 1

## 验证

- Node 定向测试：8 passed（部门/角色、标签、岗位、成员、参与范围、监管关系、资源授权）。
- `pnpm --filter @safety-training/pc-web typecheck`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- 新增/修改文件定向 ESLint：通过。
- `git diff --check`：通过。

## 已知项

全量 PC lint 被既存 `AssetPreviewPanel.vue` 的单条格式警告阻断；该文件不属于本 Task，未扩大修改范围。

## 结论

Task 1 前端交付通过；真实后端联调随 Task 2 的课程真实接口闭环统一执行。
