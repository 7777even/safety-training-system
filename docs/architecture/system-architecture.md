# 系统架构

系统采用 Monorepo，包含独立演进的 PC Web、移动端、Spring Boot 后端和 MySQL。前后端通过版本化 API Contract 协作，数据库通过 migration 演进。

- PC Web：Vue 3 + TypeScript + Vite（暂定），承载门户、用户中心和管理后台。
- 移动端：uni-app + Vue 3 + TypeScript（暂定），为移动 H5、App、企业微信预留。
- 后端：Spring Boot（暂定），提供统一 API、业务服务、鉴权和数据访问。
- 数据库：MySQL，migration 是唯一结构和数据变更入口。

前端只控制展示，后端执行真实鉴权；API Contract 优先于实现；未确认细节标记为“暂定”。

