# Proposal：初始化前端工程规范与基础设施

## 背景

现有 Monorepo 已具备 PC Web、uni-app 移动端和 shared workspace 的最小工程骨架，但尚未统一前端目录、Less
样式、依赖管理后的工程能力、代码质量配置及基础设施边界。

## 目标

在不实现业务功能和真实业务接口的前提下，建立两端一致的前端工程规范、基础目录、通用请求结构、状态管理、路由壳层与共享类型资产。

## 用户价值

前后端开发人员可以在已确认 API Contract 的前提下并行开发，且新增页面、类型、状态和工具时具有明确、可验证的落点。

## 范围

- 为 PC Web 配置 Element Plus 按需自动导入、Pinia、Axios、Mitt、路由动态导入、路径别名和 Less 基础样式。
- 为移动端配置 Pinia、uni.request 基础封装、路径别名和 Less 基础样式；不引入 Element Plus 或 Vue Router。
- 为 shared 建立无平台依赖的常量、类型、工具和导出入口。
- 添加统一的 ESLint、Prettier、EditorConfig 配置，并补齐 workspace 的 lint 与 typecheck 命令。
- 更新 README 与分层 AGENTS.md，明确命名、目录、请求、状态、路由与样式规则。

## 非目标

- 不实现课程、考试、用户、证书或监管等业务页面、接口、Mock 数据或业务逻辑。
- 不定义新的真实 API Contract、错误码或权限模型。
- 不改动后端代码、数据库结构或生产环境配置。
