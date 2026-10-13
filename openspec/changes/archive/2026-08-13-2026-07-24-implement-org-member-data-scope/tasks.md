# Tasks：机构成员、标签岗位与角色数据范围（BASE-02）

> 状态：已完成（2026-07-24）。依据已评审数据模型直接实施；前端页面不在本 Change。

## Task 1：系统表扩展、机构成员/标签/岗位表与 schema 同步

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：已评审 `00_system_permission_extension.sql`、`01_org_common.sql`（成员/标签/岗位部分）；已有 `sts_org_organization`
- 输出：正式 migration；`schema-system.sql` / `schema-org.sql`；Entity/Mapper；IT 装载；init 种子兼容
- 前后端影响：仅后端/DB/init
- API 依赖：无（本 Task 不暴露业务 API）
- 数据影响：扩展 dept/role；新增 role_dept 与 org 五表；历史回填默认机构
- 验收标准：
  - [x] 空库按 migration 顺序可执行；已有默认机构库可回填
  - [x] 机构内 dept_code / role_code / tag_name / position_code / (org,user) 唯一约束真库验证（`OrgTagMapperIT`）
  - [x] 软删后唯一值可复用（`OrgTagMapperIT`）
  - [x] init 种子可写入带 organization_id 的部门与角色
- 测试方式：Mapper IT（Testcontainers）；既有 `RbacPermissionQueryIT` 已适配新列

## Task 2：机构成员、标签、岗位管理能力

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：Task 1；design §4
- 输出：`com.sts.org.*` CRUD；OpenAPI `org.openapi.json`；权限点；零 DB + IT
- API 依赖：新建 org Contract
- 数据影响：无新增表
- 验收标准：
  - [x] 标签/岗位机构内 CRUD、启停用、删除保护（被成员引用不可删）
  - [x] 成员绑定用户，校验用户主部门属同机构；工号机构内唯一（空工号允许多条）
  - [x] 成员可维护标签与岗位（含唯一主岗位）
  - [x] 功能权限 `@SaCheckPermission` 生效并登记 menus.json
- 测试方式：`OrgTagServiceImplTest`；`OrgTagMapperIT`

## Task 3：角色数据范围配置与解析服务

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：Task 1～2；design §4.1～4.2
- 输出：角色 API 扩展字段；`us_sys_role_dept` 维护；`DataScopeService`；system-admin OpenAPI 同步
- API 依赖：扩展 `/system/roles`、`/system/depts`
- 数据影响：无新增表
- 验收标准：
  - [x] 创建/更新角色可配置六类 data_scope；CUSTOM 时维护 role_dept（含 includeChildren）
  - [x] 非 CUSTOM 不得残留 role_dept
  - [x] 角色编码机构内唯一；角色与自定义部门同机构
  - [x] DataScopeService：superAdmin 放行；多角色并集；无权限角色不贡献范围
  - [x] 权限变更清理相关缓存
- 测试方式：`SysRoleServiceImplTest`、`DataScopeServiceImplTest`、`SysRoleControllerTest`、`SysDeptServiceImplTest`
