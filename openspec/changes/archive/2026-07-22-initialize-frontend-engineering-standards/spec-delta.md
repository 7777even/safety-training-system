# Spec Delta：前端工程基础设施基线

## 新增规范

- PC Web 与移动端统一使用 Less 作为样式预处理器。
- PC Web 使用 Element Plus 官方组件和 Vite 按需自动导入，禁止全量注册。
- 两端通过 `@` 和 `@shared` 使用统一路径别名，跨 package 依赖使用 `workspace:*`。
- shared 只能放置无平台依赖的类型、API 模型、常量与纯工具。
- PC Web 使用 Vue Router 动态导入路由组件；移动端继续使用 uni-app `pages.json`。
- 请求、Pinia、EventBus 仅提供基础能力，任何业务接口必须先更新 `docs/api`。
