# Design：前端工程规范与基础设施

## 技术方案

- 前端依赖继续使用根目录 pnpm workspace，锁文件仅保留根目录 `pnpm-lock.yaml`。
- PC Web 使用 Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios 和 Mitt；Element Plus 仅由 Vite 插件按需自动导入。
- 移动端继续使用 uni-app、Vue 3、TypeScript 与 Pinia；请求层基于 `uni.request` 封装，不复用 Axios 实现。
- 两端基础样式统一使用 Less，并只在各自 workspace 安装 `less` 开发依赖。
- `@` 指向当前应用 `src`，`@shared` 指向 `frontend/shared/src`；应用仍通过 `workspace:*` 声明对 `@safety/shared` 的依赖。

## 目录与边界

PC Web 建立 API、组件、常量、布局、工具、Mock、路由、Store、样式、类型和 Portal/User Center/Admin 页面根目录。移动端建立
API、组件、常量、工具、Mock、页面、Store、样式、类型目录。shared 只包含纯 TypeScript 常量、类型、工具和导出入口。

页面与组件只提供基础壳层或空状态，不携带业务数据。路由只声明 `/portal`、`/user-center`、`/admin` 的占位入口，所有路由组件均使用动态导入。PC
端权限 Store 只为前端展示控制保留状态，后端仍是唯一真实鉴权边界。

## 请求与状态

PC 请求封装提供 `baseURL`、请求/响应拦截器、Token 注入和统一错误处理的扩展点，不声明业务接口。移动端请求封装提供相同语义的
`uni.request` Promise 包装。共享层定义泛型 API 响应类型。

两端仅创建基础 Pinia Store：用户状态、应用状态，以及 PC 的权限展示状态；不写入业务数据或硬编码角色语义。PC EventBus 以 Mitt
单例封装，移动端仅保留按需使用的边界说明。

## 质量与风险

ESLint 使用 Vue 3 与 TypeScript 基础规则，Prettier 与 EditorConfig 统一格式；Prettier 配置中的 `ignorePath`
属于有效字段。构建、lint、类型检查作为验收项。由于无业务接口，本 change 不新增 `docs/api` 契约。
