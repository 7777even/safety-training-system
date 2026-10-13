# QA：后台退出登录入口

## 范围

- 后台右上角用户名的退出登录 Popover。

## 验证结果

- 点击用户名显示“退出登录”操作。
- 点击退出登录调用既有 `userStore.logout()`，由其请求 `/api/auth/logout` 并清理本地令牌、用户资料和角色数据。
- 服务端退出请求异常时仍清理本地会话并跳转至 `/login`，避免继续访问后台页面。
- 已通过 Prettier、ESLint、`pnpm typecheck`、`pnpm build:pc` 与 `git diff --check`。

用户已取消自动化测试框架，本次未新增测试框架或测试用例。
