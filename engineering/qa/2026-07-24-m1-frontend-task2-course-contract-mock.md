# QA：M1 前端 Task 2 课程管理 Contract Mock

日期：2026-07-24  
Change：`2026-07-24-implement-m1-frontend-foundation-course` Task 2

## 验证

- 课程定向 Node 测试：30/30 通过，覆盖分类、设置、CRUD、编排、素材引用、版本、开课、报名、学习详情及 Mock/真实分支边界。
- `pnpm typecheck`、定向 ESLint、`pnpm build` 与 `git diff --check`：通过。

## 结论

后端补充接口尚未确认可联调，课程页面继续使用 Contract Mock；未将 Mock 当作真实联调证据。

## 2026-07-27 回归补充

- M1 机构与开课报名相关定向 Node 回归：19/19 通过。
- PC `typecheck`、`build`、`git diff --check` 通过。
- 新增机构仅覆盖当前 Contract 的分页、详情和创建；机构选择使用统一 API Mock/真实分支，机构成员和联系人从系统用户分页列表选择。
