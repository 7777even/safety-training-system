# 开发环境说明

## 当前状态
本仓库仅完成治理骨架，尚未初始化 Vue、uni-app 或 Spring Boot 工程，暂不提供可执行启动命令。

## 后续开发环境（暂定）
- Node.js：用于 PC Web、移动端构建，具体 LTS 待首个前端 OpenSpec 确认。
- JDK：用于 Spring Boot，版本待后端初始化 OpenSpec 确认。
- MySQL：用于本地开发，版本、账号、库名待数据库初始化 OpenSpec 确认。

本地数据库变更也必须通过 `backend/sts-service-admin/src/main/resources/db/migration/` 验证；环境变量、密钥与真实连接信息不得提交。

