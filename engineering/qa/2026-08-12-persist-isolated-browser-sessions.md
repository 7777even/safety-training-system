# QA：持久化且隔离的浏览器登录会话

## 范围

验证 PC Web 使用持久化会话槽位保存多个账户，并按标签页绑定不同槽位；不涉及后端 API、权限模型或数据库。

## 结果

- `node --test frontend/pc-web/tests/browser-tab-session-isolation.test.mjs frontend/pc-web/tests/persistent-session-slots.test.mjs`：2/2 通过。
- `pnpm exec eslint src/stores/modules/user.ts src/views/pages/LoginPage.vue --max-warnings=0`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `pnpm --filter @safety-training/pc-web typecheck`：未通过，既有 `tsconfig.json` 的 `ignoreDeprecations` 配置触发 TS5103。
- `git diff --check`：通过（仓库其他未提交文件仅产生既有换行提示）。

## 验收结论

会话槽位、标签页绑定、恢复入口、单槽位移除和占用保护已完成；全工程 typecheck 的既有配置问题需另行处理。
