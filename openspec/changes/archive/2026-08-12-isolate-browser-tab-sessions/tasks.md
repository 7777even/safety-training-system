# Tasks：隔离浏览器标签页登录会话

## Task 1：隔离 PC Web 登录会话存储

- 状态：已完成（2026-08-12）
- Owner：fxw11
- 输入：现有 PC Web 用户 Pinia store、登录 API 和路由守卫
- 输出：按浏览器标签页/窗口隔离的登录令牌读写与会话清理
- 前后端影响：仅 PC Web 前端；后端接口和服务端会话不变
- API 依赖：复用 `/api/auth/login`、`/api/auth/logout`、`GET /api/system/users/profile`
- 数据影响：不改数据库；将浏览器端令牌存储从 `localStorage` 调整为 `sessionStorage`
- 验收标准：
  - 同一浏览器中独立标签页 A、B 可分别登录账户 A、B，互不覆盖。
  - 标签页 A 刷新后仍恢复账户 A。
  - 标签页 A 退出登录或重新登录其他账户，不影响标签页 B。
  - 请求仍只携带当前标签页对应的 Bearer Token。
  - 不新增跨标签页登录同步行为。
- 测试方式：已先增加会话存储隔离行为测试并观察失败，再完成最小实现；会话隔离测试通过，PC build 通过。`typecheck` 因既有 `tsconfig.json` 的 `ignoreDeprecations` 配置报 TS5103 未通过；`lint` 因既有未使用变量错误未通过；全量合约测试存在与本 Task 无关的既有失败。
