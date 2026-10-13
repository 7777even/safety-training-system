# Design：工程治理骨架

## 目录设计

Monorepo 中：`frontend/` 存放 PC Web、移动端和共享内容；`backend/` 预留 Spring Boot；`database/` 管理 migration、DDL 参考、种子数据；
`docs/` 管理长期知识；`engineering/` 管理 QA、发布、复盘；`openspec/` 管理变更和稳定规格。

## 协作设计

每项业务能力从 OpenSpec change 开始。先产出 API Contract，前端依契约构建 Mock，后端实现契约，testHarness
验证。前端控制权限展示，后端真实授权；所有数据库变更只通过 migration 交付。

## 暂定决策

PC Web 使用 Vue 3 + TypeScript + Vite；移动端使用 uni-app + Vue 3 + TypeScript；后端使用 Spring Boot +
MySQL。迁移工具、技术版本、企业微信和 App 细节待后续 OpenSpec 确认。

