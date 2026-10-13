# Proposal：初始化可运行基础工程

## 目标

在既有 Monorepo 治理骨架上，创建可启动但不包含业务功能的 PC Web、移动端和 Spring Boot 基础工程。

## 范围

- PC Web：Vite + Vue 3 + TypeScript，基础 layout、路由、API 与 Mock 目录。
- 移动端：官方 uni-app Vue 3 + Vite 模板，基础 layout、页面、API 与 Mock 目录。
- 后端：Java 21、Spring Boot 3.4.13、Maven，创建分层包结构和应用启动类。
- 保留 `frontend/shared/` 作为跨端公共类型和纯工具目录。

## 非目标

不实现课程、考试、用户、权限、管理、数据库表、业务接口或真实 API Contract。
