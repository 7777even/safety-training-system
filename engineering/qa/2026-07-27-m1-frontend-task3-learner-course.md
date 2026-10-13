# QA：M1 前端 Task 3 学员课程学习

日期：2026-07-27
Change：`2026-07-24-implement-m1-frontend-foundation-course` Task 3

## 验证结果

- TDD：新增 PC、移动端定向测试后先运行，因 API、Mock 与页面不存在而失败；实现后 PC 3/3、移动 2/2 通过。
- PC：`pnpm typecheck`、新增文件定向 ESLint、`pnpm build` 通过。
- 移动端：`pnpm lint`、`pnpm typecheck`、`pnpm build:h5` 通过。
- `git diff --check` 通过。

## 覆盖范围

- 本人课程、可报名课程、自主报名、任务清单、学习会话、允许条件下的退选和已完成课程评价。
- PC 使用既有 `/portal/class` 菜单入口；移动端采用卡片列表和独立任务详情页。
- 不展示未有 Contract 支持的媒体预览或播放。

## 阻塞与结论

后端 Controller 与测试已存在，但当前没有可访问的联调环境和学员测试账号；保留 Contract Mock 分支，未将 Mock 当作真实联调证据。Task 3 完成，Task 4 必须在真实接口可访问后执行。
