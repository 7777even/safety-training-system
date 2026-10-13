# 后端模块边界

## 职责矩阵

| 模块                         | 形态              | 允许                                                                       | 禁止                          |
|----------------------------|-----------------|--------------------------------------------------------------------------|-----------------------------|
| **sts-common**             | jar 库           | `Result`/`PageRequest`、基类实体、公共异常与工具                                      | 可执行 Boot 主类、具体业务 Controller |
| **sts-service-admin**      | Spring Boot     | HTTP REST、MySQL 系统管理（RBAC）、培训业务（课程/考试/培训等，按领域独立包）、Redis、Sa-Token、本地/文件存储 | 跨边界写无关中间件消费者                |
| **sts-service-admin-init** | Spring Boot CLI | Flyway 空库初始化、JDBC 种子数据、BCrypt 写初始账号/角色/菜单                                | 长期常驻 Web 服务、承载业务 CRUD API   |

2026-07-21 确认：本项目不采用微服务拆分，培训各业务域（课程、考试、培训等）作为 `com.sts.<domain>.*` 独立顶级包
（与 `com.sts.admin` 平级，例如课程域 `com.sts.course`）并入 `sts-service-admin` 同一进程和同一 JAR，
共用其 MyBatis-Plus/Sa-Token/Redis/异常处理基础设施；不新建独立 pom、启动类、端口或服务注册。
`AdminApplication` 的 `@ComponentScan`/`@MapperScan` 需同时覆盖 `com.sts.admin` 与各业务域包；
`HttpApiExceptionHandler`、`ResultEasyTransAdvice` 的 `basePackages` 同理需要登记新增业务域包。
领域包之间通过各自的 Service 接口协作，禁止跨域直接操作彼此的 Entity/Mapper。

## sts-service-admin 数据边界

- **MySQL**：用户、角色、菜单、部门、字典、参数、登录/操作日志、文件元数据等 `us_sys_*` 系统表；课程等培训业务使用 `sts_*`
  表，逻辑外键关联 `us_sys_*`，不建物理外键。
- DDL 真相源：`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` 是 init 模块化完整结构，
  `backend/sts-service-admin/src/main/resources/db/migration/V*.sql` 是 Admin 增量；init 建库后登记 baseline，集成测试复用同一批完整
  SQL。

## 强制规则

1. 系统管理能力集中在 `sts-service-admin`，公共类型进 `sts-common`。
2. 初始化与运行态服务分离：拓荒用 `sts-service-admin-init`，日常服务用 `sts-service-admin`。
3. 构建使用 Maven（`backend/pom.xml`），禁止为模块单独硬编码依赖版本。
