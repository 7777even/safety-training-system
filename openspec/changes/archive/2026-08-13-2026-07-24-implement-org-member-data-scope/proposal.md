## Why

课程与素材已挂 `organization_id`，但仅有默认机构占位：无机构成员档案、标签/岗位，角色也无机构归属与六类 `data_scope`。阶段 1
主线硬依赖这些能力，否则无法替换单机构临时边界，也无法支撑后续参与范围与真实联调。

依据已评审数据模型：`openspec/changes/archive/2026-07-22-design-safety-training-mysql-schema/`。

## What Changes

- 扩展 `us_sys_dept` / `us_sys_role`，新增 `us_sys_role_dept`；历史数据回填到默认机构。
- 落地 `sts_org_member`、`sts_org_tag`、`sts_org_member_tag`、`sts_org_position`、`sts_org_member_position`。
- 提供机构成员、标签、岗位管理 API；角色创建/更新支持机构归属、`data_scope` 与 CUSTOM 部门范围。
- 提供服务端数据范围解析（六类语义并集），供后续业务查询复用。
- 同步 OpenAPI、权限点、开发期 schema 快照与 init 种子。

## Capabilities

### New Capabilities

- `org-member-data-scope`：机构成员档案、标签/岗位、角色数据范围与解析。

### Modified Capabilities

- 系统管理角色/部门：机构内唯一角色编码、部门机构归属字段。

## Non-goals

- 参与范围（BASE-03）、跨机构资源授权与监管关系（BASE-04）。
- 前端管理页面（本 Change 交付后端 + Contract；页面另开或后续联调）。
- 废弃 `DefaultOrganizationService` 的全量替换（解析能力先落地，课程域逐步切换）。

## Impact

- `database/migration`、`backend/sql/schema-system.sql`、新建 `schema-org.sql`
- `com.sts.org.*`、`com.sts.admin` 角色/部门扩展、`sts-service-admin-init` 种子
- `docs/api/system-admin.openapi.json` 与新建 `docs/api/org.openapi.json`
