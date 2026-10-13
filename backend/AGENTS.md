# 后端协作规范

## 协作身份（lims）

本仓库含前端。当 `git config user.name` 为 **`lims`** 时：

- 只实施 `backend/`、`docs/api/` 及相关 OpenSpec/工程记录；**禁止**改 `frontend/`。
- **不关注前端**：proposal/design/tasks 不含页面、Mock、联调、UI 验收；「前后端影响」为「仅后端；前端以 OpenAPI 为准另开」。
- 给前端的交付物是按规范写好的 `docs/api/*.openapi.json`，不负责前端对接实施。

详见根 `AGENTS.md` §17.1 与 `.cursor/rules/backend-only-collaboration.mdc`。

## Spring Boot 分层规范
后端采用 Spring Boot + MySQL（暂定），按职责分层：
- Controller：HTTP 协议、DTO 接收、鉴权入口、响应封装，不承载业务规则。
- Service：业务编排、事务边界、状态与权限规则。
- Mapper：数据库访问，不泄漏到 Controller。
- Entity：持久化对象，与表结构对应。
- DTO：接口入参或服务输入，必须参数校验。
- VO：接口出参，不直接暴露 Entity。

## 配置管理规范

1. 应用自定义配置必须按稳定前缀建立 `@ConfigurationProperties` 类型，不得在 Controller、Service、Component 或
   Configuration 中分散使用 `@Value` 注入配置项。
2. 配置消费者必须通过构造器注入对应 Properties 类；同一前缀下的关联配置集中管理，层级配置使用嵌套类型表达，禁止为单个配置项创建零散包装类。
3. Properties 类负责类型、默认值和配置结构，业务类不得重复声明配置默认值或通过反射修改配置字段。
4. Spring Boot、数据源、Redis、MyBatis、Sa-Token 等框架已有原生绑定能力的配置继续使用框架配置，不重复包装为项目 Properties 类。
5. `@ConditionalOnProperty`、`@Scheduled` 等注解中的配置键属于框架声明，可以保留；不得借此重新引入字段或构造参数级
   `@Value`。
6. 新增或调整自定义配置时，必须同步检查配置文件、Properties 类、消费者和相关测试，确保配置键、类型与默认语义一致。

## 权限与接口
权限必须由后端真实校验，不依赖前端菜单或路由。API 的新增、字段/错误码调整或废弃必须同步 `docs/api/` 和对应 OpenSpec。
OpenAPI 写法强制遵循 `docs/architecture/openapi-contract-writing-guide.md`（样板：`docs/api/asset.openapi.json`、
`docs/api/org.openapi.json`）。四条铁律：按 Controller 类名 `tags` 分类（禁止平铺）、每个接口有
`summary`/`description`、每个字段有中文 description、每个接口有 example；并写 `x-permission` 与错误码，与
Controller/DTO/VO 对齐。

## 数据库

所有表、索引、字段、约束、数据修复或正式初始化数据变更必须同时更新
`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` 中受影响模块的最新完整结构，并在
`backend/sts-service-admin/src/main/resources/db/migration/` 新增 Flyway `V<version>__description.sql`。完整 SQL 禁止
`ALTER TABLE`；`V` 只表达本次增量；`sts.init.schema-version` 必须同步为当前版本。禁止只改 Entity、修改已执行 `V`、依赖手工数据库操作或为
Admin 启用 `baseline-on-migrate`。

## 异常与参数校验
Controller 使用 DTO 和声明式参数校验，Service 负责跨字段、状态和权限校验。统一异常处理必须转换为已定义 API 错误结构，不得泄漏堆栈、SQL、内部实现或敏感信息。
