# Spec Delta：后端工程结构

## ADDED

### 后端 Maven 多模块底座

系统后端以 Maven 多模块承载系统管理底座：

- `sts-common`：跨服务公共库
- `sts-service-admin`：系统管理 Spring Boot 服务
- `sts-service-admin-init`：管理端种子数据初始化 CLI

构建工具为 Maven；运行时基线为 JDK 21、Spring Boot 3.4.13。

### DDL 脚本落位

系统管理相关 DDL 位于 `backend/sql/`，作为建表与集成测试初始化脚本来源。

## REMOVED

### 空壳单模块后端骨架

原 `backend/` 下占位启动类与空包结构不再作为可运行后端入口。
