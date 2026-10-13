# Proposal：移植后台基础模块到 Maven 多模块后端

## 背景

`source-backend`（Gradle 多模块模板）已具备可复用的系统管理底座：`sts-common`、`sts-service-admin`、`sts-service-admin-init`
。当前仓库 `backend/` 仍是空壳单模块 Spring Boot 工程，无法承载后续安全培训业务。

## 目标

将上述三个模块连同代码与依赖关系整体迁入 `backend/`，并把构建体系从 Gradle 转为 Maven 多模块，保证可编译。

## 用户价值

- 后续培训业务可基于已有 RBAC、鉴权、字典、日志等系统能力扩展。
- 后端构建与仓库约定统一为 Maven（JDK 21 / Spring Boot 3.4.13）。

## 功能范围

- 将 `sts-common`、`sts-service-admin`、`sts-service-admin-init` 源码与资源迁入 `backend/`。
- 以 Maven 父 POM + 子模块重建依赖与 annotation processor（Lombok / MapStruct / EasyTrans）。
- 同步迁入 `sql/schema-system.sql` 等建表脚本，供集成测试与初始化工具使用。
- 移除原空壳单模块启动类与占位包。

## 非目标范围

- 不迁移 `sts-service-engine`、`sts-service-driver`。
- 不改包名、不改业务逻辑、不新增业务接口。
- 不调整生产环境配置，不执行数据库迁移上线。
- 不强制本机跑通依赖外部 MySQL/Redis/Nacos/Docker 的集成测试。
