# Design：Gradle → Maven 多模块移植

## 系统设计

`backend/` 调整为 Maven 聚合工程：

```
backend/
  pom.xml                    # parent（packaging=pom）
  sts-common/                 # 公共库 jar
  sts-service-admin/          # Spring Boot 可执行应用
  sts-service-admin-init/     # 种子数据 CLI 应用
  sql/                       # DDL 真相源（自 source-backend 迁入）
  .mvn/settings.xml
```

## 坐标与版本

| 项                 | 取值               |
|-------------------|------------------|
| groupId           | `com.sts`        |
| version           | `1.0.0-SNAPSHOT` |
| Java              | 21               |
| Spring Boot       | 3.4.13           |
| MyBatis-Plus      | 3.5.15           |
| Sa-Token          | 1.45.0           |
| Redisson          | 4.3.1            |
| MapStruct         | 1.6.3            |
| EasyTrans         | 2.0.0            |
| Caffeine          | 3.2.4            |
| Log4j2            | 2.25.4           |
| MySQL Connector/J | 9.6.0            |

父 POM 继承 `spring-boot-starter-parent`，并 import Spring Cloud / Spring Cloud Alibaba BOM，与源 Gradle
`spring-boot-bom.gradle` 对齐。

## 模块职责

- **sts-common**：API 包装、基类实体、全局异常、分页等公共能力；jar，非 Boot 应用。
- **sts-service-admin**：系统管理 HTTP 服务；依赖 sts-common；Log4j2；排除默认 Logback。
- **sts-service-admin-init**：非 Web CLI；JDBC + BCrypt 写入种子数据；依赖 sts-common（与源一致）。

## 构建取舍

- 日志：全模块排除 `spring-boot-starter-logging`，使用 `spring-boot-starter-log4j2` / Log4j2 实现。
- 注解处理：Maven Compiler Plugin 配置 Lombok → MapStruct → EasyTrans 顺序；引入 `lombok-mapstruct-binding`。
- 集成测试：保留 `src/integrationTest`；通过 build-helper + failsafe 可选执行；默认 `mvn test` 仅跑零 DB 单测。
- SQL：`backend/sql/schema-system.sql` 在 admin 集成测试资源阶段拷贝到 `schema/`。

## 风险

- 暂定：模块与包名已统一为 `sts-*` / `com.sts.*`。
- Nacos / Redis / MySQL 运行依赖仍需外部环境，本次只保证编译与单元测试可执行。
