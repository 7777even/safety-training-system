# Tasks：补齐系统菜单管理 CRUD

## Task 1：完成菜单维护 CRUD 能力

- **Owner**: AI
- **输入**: 已确认的菜单删除策略、`SysMenu` 实体、现有菜单树/可见性 Service、`us_sys_menu` 表结构
- **输出**: 菜单新增、修改、删除 DTO、Service、Controller 及 OpenAPI 契约
- **前后端影响**: 前端可调用菜单维护 API；后端新增三个受权限保护的写接口
- **API 依赖**: `ResultEasyTransAdvice`、`docs/api/system-admin.openapi.json`、现有 `sys:menu:*` 权限
- **数据影响**: 无迁移；新增/修改/逻辑删除 `us_sys_menu` 行；删除前读取 `us_sys_role_menu` 依赖
- **验收标准**:
  - 三个接口 URL、方法和权限符合 design
  - 父菜单、循环树、子菜单与角色关联约束得到校验
  - 更新使用乐观锁版本号
  - OpenAPI 含完整请求/响应示例
- **测试方式**: Controller MockMvc 单测、Service Mockito 单测、Testcontainers MySQL 集成测试
- **状态**: 已完成

## Task 2：完成菜单 CRUD 测试与审查记录

- **Owner**: AI
- **输入**: Task 1 代码与 API 契约
- **输出**: 不带数据库与带数据库的自动化测试，以及 QA/Retro 记录
- **前后端影响**: 无运行时接口变化；为交付提供可追溯验证结果
- **API 依赖**: Task 1 的菜单 CRUD 接口
- **数据影响**: 集成测试仅使用 Testcontainers 临时 MySQL
- **验收标准**:
  - 无数据库测试覆盖核心业务校验和 Controller 入参校验
  - 带数据库测试覆盖真实写入、逻辑删除和关联约束
  - 相关 Gradle 测试通过，QA 和 Retro 记录结论
- **测试方式**: `gradle :sts-service-admin:test` 与集成测试任务
- **状态**: 已完成
