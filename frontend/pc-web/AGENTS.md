# PC Web 协作规范

## 模块

PC Web 采用 Vue 3 + TypeScript + Vite，包含：

- Portal：统一门户，路由 `/portal`。
- User Center：个人资料、学习和考试相关个人操作，路由 `/user-center`。
- Admin Console：系统管理，路由 `/admin`。

## 访问与路由规则

- 普通用户只能访问 `/portal`、`/user-center` 及其已约定子路由。
- 管理员可在门户或用户中心看到“进入后台管理”按钮，入口为 `/admin`。
- 三个根路由的子路由、重定向和页面权限须先在路由设计或 API Contract 明确。
- 前端路由守卫只改善体验，不能作为安全边界。

## 权限边界

前端只控制菜单、按钮和页面展示；后端必须对 `/admin` 和所有受保护 API 做真实认证与授权校验。

## 工程实现规范

- 使用 pnpm workspace 管理依赖，引用 `@safety/shared` 必须使用 `workspace:*`；禁止 npm/yarn。
- Vue 文件使用 `<script setup lang="ts">`、Composition API 和 scoped Less；组件、页面使用 PascalCase，目录使用 kebab-case。
- Element Plus 只使用官方组件和 Vite 按需自动导入，禁止全量 `app.use(ElementPlus)`；图标从 `@element-plus/icons-vue` 按需导入。
- 目录职责：`api` 为请求封装，`constants` 为常量，`libs` 为纯工具，`stores` 为 Pinia，`router` 为动态路由，`styles` 为 Less 基础样式，`types` 为 TypeScript 类型，`mock` 为契约 Mock。
- 页面按 Portal、User Center、Admin Console 分别放入 `views/portal`、`views/user-center`、`views/admin`；通用页面根组件放入 `views/pages`。
- 开发 `/portal` 页面时，必须以同类门户现有页面为基准并遵循 `docs/design/pc-web-ui-standard.md` 的“前台门户”章节；不得复用 `/admin` 的 `admin-*` 容器、后台筛选工具栏、面包屑、tabs-card 或表格工作区。开发 `/admin` 页面时反向遵循后台章节。
- 新增接口前必须先更新 `docs/api`；Request 只提供通用拦截、Token 与错误处理能力，不得在页面堆叠请求逻辑。
- EventBus 只处理轻量全局事件；复杂跨组件状态使用 Pinia。常量不得以魔法值散落在页面、Store 或路由中。
