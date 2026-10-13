# 后端模块布局

> 目录与分包约定。版本见 [backend-tech-stack.md](./backend-tech-stack.md)。

**Java 包基准**：`com.sts`

## 目录结构

```text
backend/
├── pom.xml                         # 父 POM（packaging=pom）
├── .mvn/settings.xml
├── sts-common/                     # 公共库 jar
│   └── src/main/java/com/sts/common/
├── sts-service-admin/              # 系统管理 + 培训业务 Spring Boot 应用（单进程，不拆微服务）
│   └── src/main/java/com/sts/
│       ├── admin/                  # 系统管理域
│       │   ├── AdminApplication.java   # @ComponentScan/@MapperScan 覆盖 com.sts.admin 与各业务域包
│       │   ├── auth/                   # 登录鉴权
│       │   ├── system/                 # 用户/角色/菜单/部门/字典/配置/日志/文件
│       │   ├── mapper/
│       │   ├── entity/
│       │   ├── config/
│       │   ├── aop/                    # 操作日志、脱敏等
│       │   ├── cache/                  # 缓存失效与本地缓存
│       │   └── display/                # ID→Name Feeder / Advice
│       └── course/                 # 培训业务域：课程（分类/设置/…），与 admin 平级、同进程同 JAR
│           ├── entity/ mapper/ controller/ converter/ dto/ service/ vo/
└── sts-service-admin-init/         # 完整建库、baseline 与种子 CLI
    └── src/main/java/com/sts/init/
```

数据库 SQL归属各自模块：`sts-service-admin-init/src/main/resources/schema/schema-*.sql` 由 init 完整建库，
`sts-service-admin/src/main/resources/db/migration/V*.sql` 由 Admin Flyway 增量升级。

## sts-service-admin 分层

| 层 | 包路径 | 职责 |
|----|--------|------|
| Controller | `com.sts.admin.*.controller` | HTTP、DTO、权限注解、返回 `Result<T>` |
| Service | `com.sts.admin.*.service` | 业务编排、事务、权限规则 |
| Mapper | `com.sts.admin.mapper` | MyBatis-Plus 访问 MySQL |
| Entity | `com.sts.admin.entity` | 表映射 |
| Converter | `com.sts.admin.system.converter` | MapStruct Entity↔VO / DTO→Entity |

公共 API 包装、基类实体、全局异常位于 `sts-common`（`com.sts.common.*`）。

## 相关文档

- 模块边界：[backend-module-boundary.md](./backend-module-boundary.md)
- 鉴权：[auth-design.md](./auth-design.md)
- API 契约：[api-standard.md](./api-standard.md)
