# 安全教育培训系统

## 项目说明

本系统采用 Monorepo 管理 PC Web、移动端、后端、数据库迁移与工程治理文档。

## 目录说明

- `openspec/`：变更提案、设计、任务和稳定规格。
- `docs/`：需求、架构、接口契约、部署等长期知识。
- `engineering/`：QA、发布检查、复盘。
- `frontend/`：PC Web、移动端和跨端共享内容。
- `backend/`：Spring Boot 预留位置。
- `database/`：迁移、DDL 参考和种子数据。

## 技术栈

- PC Web：Vue 3 + TypeScript + Vite
- 移动端：uni-app + Vue 3 + TypeScript
- 后端：Spring Boot
- 数据库：MySQL

## 包管理

前端 Monorepo 统一使用 pnpm workspace，workspace 范围为 `frontend/*`。

- 进入 `frontend/` 后执行依赖安装：`corepack enable` 后运行 `pnpm install`。
- PC Web 与移动端通过 `workspace:*` 引用 `@safety/shared`。
- 禁止混用 npm 或 yarn；仓库只保留 `frontend/pnpm-lock.yaml`。

### 常用命令

```bash
cd frontend

# 启动 PC Web
pnpm dev:pc

# 启动移动 H5
pnpm dev:mobile

# 构建 PC Web / 移动 H5
pnpm build:pc
pnpm build:mobile

# 执行已配置的 lint / typecheck 脚本
pnpm lint
pnpm typecheck
```

## 前端工程规范

- `frontend/pc-web/`：Vue 3 + TypeScript + Vite + Element Plus。使用 `<script setup lang="ts">`、动态路由、Pinia、Axios、Mitt 和 Less；Element Plus 必须按需自动导入，禁止全量注册。
- `frontend/mobile-app/`：uni-app + Vue 3 + TypeScript。使用 uni-app `pages.json`、Pinia、`uni.request` 与 Less；不使用 Element Plus 或 Vue Router，并为 H5、App、企业微信入口保留边界。
- `frontend/shared/`：只存放跨端纯 TypeScript 类型、API 模型、常量与工具，不得放 Vue 页面、端专属组件、路由或平台 API。

PC Web 的门户、用户中心和后台管理页面分别放入 `views/portal`、`views/user-center`、`views/admin`；通用页面放入 `views/pages`。移动页面放入 `pages` 下对应的移动模块目录。组件和页面使用 PascalCase，业务目录使用 kebab-case；工具函数使用 camelCase，常量使用 UPPER_SNAKE_CASE。

新增通用工具放入 `libs`，类型放入 `types` 并通过 `index.ts` 汇总；跨端内容优先放入 `frontend/shared`。新增真实接口前必须先更新 `docs/api`，然后同步 OpenSpec、Mock 和调用方。禁止混用 npm 或 yarn。

`frontend/` 提供 `.prettierrc`、`eslint.config.mjs` 与 pnpm 配置；根目录保留全仓库通用的 `.editorconfig`。两端统一使用 Less：全局样式仅用于变量、reset 和基础布局，页面样式使用 scoped Less。
