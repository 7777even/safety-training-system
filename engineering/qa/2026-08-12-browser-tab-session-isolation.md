# QA：浏览器标签页登录会话隔离

## 范围

验证同一浏览器不同标签页使用不同账户时，PC Web 登录令牌不会互相覆盖；不涉及后端 API、权限模型或数据库。

## 结果

- `node --test frontend/pc-web/tests/browser-tab-session-isolation.test.mjs`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `pnpm --filter @safety-training/pc-web typecheck`：未通过，既有 `tsconfig.json` 的 `ignoreDeprecations` 配置触发 TS5103。
- `pnpm --filter @safety-training/pc-web lint`：未通过，既有未使用变量错误及既有格式告警。
- PC 全量 Node 合约测试：未全通过，失败项与本次会话隔离改动无关。

## 验收结论

本 Change 的会话存储隔离验收通过；typecheck、lint 和全量合约测试的既有问题需另行处理。
