## 1. 依据

沿用已评审 DDL 与权限语义：

- `ddl/00_system_permission_extension.sql`
- `ddl/01_org_common.sql` 中机构成员/标签/岗位（不含监管关系、参与范围、资源授权）
- `design.md` §4.1～4.2、§5

## 2. 数据

### 2.1 系统表扩展

| 对象                 | 变更                                                                                  |
|--------------------|-------------------------------------------------------------------------------------|
| `us_sys_dept`      | +`organization_id`、`dept_code`、`dept_type`、`description`；`uk_org_dept_code_deleted` |
| `us_sys_role`      | +`organization_id`、`role_level`、`is_builtin`、`data_scope`、`description`；角色编码改为机构内唯一 |
| `us_sys_role_dept` | 新建；仅 `data_scope=CUSTOM` 使用                                                         |

回填：现有部门/角色归属 `org_code=DEFAULT` 机构；`dept_code` 用 `DEPT_{id}`；`role_level` 默认 `ADMIN`；`superAdmin` 的
`is_builtin=1`；`data_scope` 默认 `ORGANIZATION`（非 superAdmin 业务角色）/ 保持可配置。

### 2.2 机构业务表

`sts_org_member`、`sts_org_tag`、`sts_org_member_tag`、`sts_org_position`、`sts_org_member_position`：字段与索引按已评审 DDL
原样落地。

成员不复制姓名/部门/角色；部门取自 `us_sys_user.dept_id`，且该部门的 `organization_id` 必须等于成员机构。

## 3. 包与错误码

- 新包 `com.sts.org.*`（与 course/asset 同级），挂入 `AdminApplication` 扫描。
- 业务错误码 `4000-4999`（org）；角色/部门扩展错误继续 `2000-2999`。
- Organization 实体暂仍在 `com.sts.course.entity`（避免无意义搬迁）；成员域通过 Mapper/Service 引用。

## 4. API（摘要）

| 能力   | 路径前缀               | 权限                      |
|------|--------------------|-------------------------|
| 成员   | `/org/members`     | `org:member:*`          |
| 标签   | `/org/tags`        | `org:tag:*`             |
| 岗位   | `/org/positions`   | `org:position:*`        |
| 角色扩展 | 现有 `/system/roles` | 沿用 `sys:role:*`，请求体增加字段 |

成员创建：指定 `userId` + 机构扩展字段 + 可选 tagIds/positionIds（含主岗位）。

## 5. 数据范围解析

`DataScopeService`（admin 包）：

1. `superAdmin`（userCode 精确匹配）→ 全放行。
2. 解析有效 `sts_org_member` → `principalOrganizationId`；校验用户主部门属于该机构。
3. 仅对持有目标 `permission` 的角色取 `data_scope` 并集。
4. 输出：`DataScopeContext`（`all` / `organizationIds` / `deptIds` / `selfUserId`）。

列表过滤由各业务在后续 Task 接入；本 Change 交付可单测的解析器 + 角色配置闭环。

## 6. 风险与取舍

- 角色编码唯一键从全局改为机构内：migration 先回填再换唯一键；init/单测同步改插入列。
- 本期普通账号仍单主机构；不做机构切换。
- 监管关系表不在本 Change。
