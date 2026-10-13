# Tasks：前端工程规范与基础设施

## Task 1：PC Web 基础工程能力

- 状态：已完成
- Owner：前端开发
- 输入：已初始化的 Vue 3 + TypeScript + Vite 工程、已确认的前端规范。
- 输出：Element Plus 按需自动导入、Less 样式、路径别名、动态路由壳层、PC 请求/EventBus/Pinia 基础设施。
- 前后端影响：仅前端；不调用或定义业务接口。
- API 依赖：无；仅定义与既有 Contract 对齐的泛型请求基础结构。
- 数据影响：无。
- 验收标准：`pnpm build:pc`、PC workspace lint 与 typecheck 成功；无全量 Element Plus 注册；路由页面均动态导入。
- 测试方式：执行构建、ESLint 与 vue-tsc。

## Task 2：移动端基础工程能力

- 状态：已完成
- Owner：前端开发
- 输入：已初始化的 uni-app + Vue 3 + TypeScript 工程、已确认的前端规范。
- 输出：Less 样式、路径别名、uni.request 基础封装与 Pinia 基础 Store。
- 前后端影响：仅前端；不调用或定义业务接口。
- API 依赖：无；仅定义与既有 Contract 对齐的泛型请求基础结构。
- 数据影响：无。
- 验收标准：`pnpm build:mobile`、移动端 workspace lint 与 typecheck 成功；未引入 Element Plus、Axios 或 Vue Router。
- 测试方式：执行构建、ESLint 与类型检查。

## Task 3：跨端规范与质量能力

- 状态：已完成
- Owner：前端开发
- 输入：pnpm workspace、两个前端应用的基础能力。
- 输出：shared 纯 TypeScript 目录与导出入口、统一 ESLint/Prettier/EditorConfig、README 与 AGENTS.md 规范。
- 前后端影响：仅前端；API 新增前仍必须先更新 `docs/api`。
- API 依赖：无。
- 数据影响：无。
- 验收标准：根目录 `pnpm lint`、`pnpm typecheck` 成功；shared 不含平台运行时依赖；文档说明 Less、命名、目录和边界。
- 测试方式：运行根目录质量命令、静态检查目录与依赖。
