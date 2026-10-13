# Design：移除跨机构资源授权能力

## 1. 总体设计

资源授权退役后，系统只保留租户插件和服务端当前机构上下文定义的机构边界：

```text
当前机构访问本机构资源 → 按既有功能权限和业务状态继续处理
当前机构访问其他机构资源 → 不可见、不可引用、不可管理
```

不得用父子机构、监管身份、请求参数或资源授权表扩大边界。跨机构资源应按不存在或不可引用处理，不泄露资源名称、所属机构或状态。

## 2. 后端退役

删除以下能力：

- `ResourceGrantController` 及 `/common/resource-grants/**` 路由；
- `ResourceGrantService`、实现、DTO、VO、Entity、Mapper；
- `CrossTenantResourceReader` 及其绕过租户插件的读取路径；
- `common:grant:list/add/edit/delete` 权限入口；
- 仅验证资源授权 CRUD、等级、有效期和跨租户授权读取的测试。

删除后不得保留空 Controller、返回固定拒绝的桩服务、废弃表 Entity 或兼容接口。

## 3. 下游业务收敛

### 3.1 课程详情

课程详情使用正常租户过滤的 Mapper 读取。其他机构课程不得通过旁路读取；查询不到时沿用课程不存在的安全响应。

### 3.2 课程考试内容项

课程引用考试时，考试必须属于当前课程机构并满足既有发布、启用和考试类型规则。其他机构考试按不可引用处理，不再调用授权服务。

### 3.3 培训基础资源

培训类型、培训标准和培训模板使用正常租户过滤读取，并显式保持当前机构一致性。删除 `TrainingResourceAccessHelper` 中的跨租户旁路和
`USE` 授权校验，但保留其资源存在性、状态及同机构校验职责；如职责在清理后过薄，可在当前 Task 内就地收敛到既有服务，不做无关重构。

### 3.4 多租户配置

从 `TenantLineHandlerImpl` 的豁免表集合中删除 `sts_common_resource_org_grant`。删除授权表后，应用启动和 SQL 解析均不得再引用该表。

## 4. API 与权限退役

- 删除 `docs/api/resource-grant.openapi.json`，表示该 API Contract 整体下线。
- 更新 `docs/AGENTS.md` 和 OpenAPI 编写指南中的契约清单。
- 其他 OpenAPI 中如存在跨机构授权描述，改为当前机构资源约束；不新增替代接口。
- 删除菜单树中的资源授权菜单和 `common:grant:*` 功能权限。
- API 直接下线，无废弃期；旧调用方得到 404。

## 5. 数据库迁移

新增 `database/migration/V20260811HHMMSS__remove_resource_org_grant.sql`，按以下顺序执行：

1. 删除 `us_sys_role_menu` 中指向资源授权菜单及功能权限节点的关联；
2. 删除资源授权功能权限节点和菜单节点；
3. 删除 `sts_common_resource_org_grant` 表，表内存量授权记录随表删除。

迁移使用明确菜单 ID 与权限码双重约束，避免误删其他菜单。实施时先核对现有 schema
中真实表名、主键和关联字段；若发现外键或其他业务表引用授权表，立即暂停并提交人工决策。

历史 migration 不得修改或删除。否则共享环境不会得到 DROP，且 Flyway 校验会因 checksum 改变失败。当前全量参考
`backend/sql/schema-common.sql` 则应删除授权表定义，使新环境的最终 schema 不再包含该表。

## 6. 初始化与长期文档

从当前初始化资产中删除资源授权菜单：

- `backend/sts-service-admin-init/src/main/resources/data/menus.json`；
- `backend/sts-service-admin-init/src/main/resources/data/menus-back.json`；
- `backend/sts-service-admin-init/src/main/resources/data/current-online-menu-tree.json`。

更新当前路线图、进度表、角色流程、系统菜单及架构/API 清单，明确 BASE-04 跨机构资源授权已退役，相关下游依赖改为严格同机构约束。历史
OpenSpec、QA 和归档设计保留，不伪造历史。

## 7. 测试策略

### 7.1 Red → Green

先修改或新增测试表达以下预期，并观察其在旧实现下失败：

- 其他机构课程详情不可通过跨租户旁路读取；
- 课程不能引用其他机构考试，即使历史上存在授权记录；
- 培训类型、标准和模板不能跨机构读取或引用；
- 租户豁免表不再包含 `sts_common_resource_org_grant`。

随后以最小实现使测试通过。纯删除的授权 CRUD 测试随生产能力一并删除，不把“删除后补测试”宣称为 TDD。

### 7.2 零 DB 与 MySQL IT

- 零 DB：课程、课程内容项、培训资源同机构成功路径与跨机构拒绝；租户豁免集合。
- MySQL IT：正常租户过滤无法读取其他机构资源；migration 后授权表与菜单/角色关联不存在；当前 schema 可从空库顺序构建。
- 静态检查：生产代码、当前 schema、初始化数据、当前 OpenAPI 与长期文档不存在活动的资源授权引用。

### 7.3 验证命令

在 `backend/` 执行受影响 Admin 零 DB与 integration-test；在仓库根执行 OpenAPI JSON/引用检查、`openspec validate --strict`
和
`git diff --check`。Docker 不可用时必须如实报告 IT 未执行，不得用零 DB 代替。

## 8. 回滚与风险

- 代码回滚：恢复退役前应用版本和 API Contract。
- 数据回滚：仅回滚代码不会恢复已删除授权记录，必须从 migration 执行前备份恢复授权表及数据。
- 主要风险：遗漏跨租户旁路导致越权、遗漏 Bean 依赖导致应用启动失败、遗漏菜单关联导致脏权限、旧调用方继续访问已下线路由。
- Review 发现 API、权限、数据库或架构边界与本设计不一致时，结论必须为“需人工决策”。

