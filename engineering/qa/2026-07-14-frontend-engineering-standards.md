# QA：前端工程规范与基础设施

## 范围

OpenSpec Change：`2026-07-14-initialize-frontend-engineering-standards`。

本次只验证前端基础设施、工程规范与空壳路由；不包含业务页面、业务接口或真实权限校验。

## 验证结果

- `pnpm lint`：通过。
- `pnpm typecheck`：通过。
- `pnpm build:pc`：通过。
- `pnpm build:mobile`：通过。
- 静态边界检查：PC 未全量注册 Element Plus；移动端源码未包含 Element Plus、Axios 或 Vue Router；PC 路由组件均为动态导入。
- 格式检查：`.prettierrc`、`eslint.config.mjs`、PC/Mobile 请求封装和 PC 路由配置通过 Prettier 检查。

## 已知事项

pnpm 安装时报告 uni-app 模板传递依赖的 Vue `3.4.21` peer 与当前 Vue `3.5.39` 不一致。两端构建和类型检查均通过；本次不擅自变更既有 uni-app/Vue 版本组合，后续升级模板时应单独评估。
