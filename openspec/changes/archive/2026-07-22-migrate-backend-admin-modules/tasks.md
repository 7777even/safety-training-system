# Tasks：移植后台基础模块

## Task 1：建立 Maven 多模块骨架并迁入源码

- **Owner**: AI
- **输入**: `source-backend` 中 `sts-common` / `sts-service-admin` / `sts-service-admin-init` 与 `sql/`
- **输出**: `backend/` 多模块目录、父/子 POM、源码与资源、`sql/`
- **前后端影响**: 仅后端构建结构；前端无影响
- **API 依赖**: 无新增 API Contract
- **数据影响**: 迁入 DDL 脚本文件，不直接改库
- **验收标准**:
  - 三个模块源码位于 `backend/<module>/src`
  - 父 POM 声明 modules
  - 原空壳 `backend/src` 已移除
- **测试方式**: `mvn -s .mvn/settings.xml -pl sts-common,sts-service-admin,sts-service-admin-init -am compile`
- **状态**: 已完成

## Task 2：对齐依赖与注解处理并验证编译

- **Owner**: AI
- **输入**: Task 1 产出 + 源 Gradle 版本表
- **输出**: 可编译的 Maven 工程；更新 `backend/README.md`
- **前后端影响**: 仅后端
- **API 依赖**: 无
- **数据影响**: 无
- **验收标准**:
  - `mvn compile` 成功
  - `mvn test`（单元测试，不含强制 Docker 集成测）可通过或仅因环境缺失被明确隔离
- **测试方式**: Maven compile / test
- **状态**: 已完成
