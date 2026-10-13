# QA：跨端本地登录与会话基础

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission`。

本次仅验证 Task 1：PC Web 与移动 H5 的本地账号登录、令牌会话、当前用户资料初始化、退出登录，以及 PC 路由/移动页面跳转基础。不包含门户内容、后台框架、系统设置管理或课程、考试等业务能力。

## 验证结果

- `pnpm typecheck`：通过；shared、PC Web、移动端的 TypeScript 检查均成功。
- `pnpm lint`：通过；三个 workspace 均为零错误、零警告。
- `pnpm build:pc`：通过；Vite 生产构建成功。
- `pnpm build:mobile`：通过；uni-app H5 构建成功。
- `git diff --check`：通过；本次变更无空白错误。
- 契约静态核对：登录调用 `POST /api/auth/login` 并使用 `identityType=local`；个人资料与退出分别调用 `GET /api/system/users/profile`、`POST /api/auth/logout`；受保护请求使用 `Authorization: Bearer {token}`。
- 代码自查：PC 未登录访问受保护路由会转至登录页；移动端未登录访问首页会重启到独立登录页；回跳地址仅接受站内绝对路径；密码经 Web Crypto SHA-256 摘要后提交。

## 未执行项与已知事项

- 自动化测试框架按用户要求取消，未保留或执行单元/组件测试。
- 未提供可访问的后端地址与联调账号，因此未执行真实登录、资料加载和退出接口联调；后续应在开发环境补充该验证。
- pnpm 安装期间仍报告既有 uni-app 模板传递依赖的 Vue `3.4.21` 与当前 Vue `3.5.39` 的 peer 警告。本次 typecheck、lint 与 H5 构建均通过，未擅自调整既有版本组合。
- 独立 Reviewer 未执行；当前协作约束禁止派生子代理，本次仅完成代码自查。
