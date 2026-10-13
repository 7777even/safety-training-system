# Spec Delta：org-member-data-scope

## ADDED Requirements

### Requirement: 机构成员档案

系统 SHALL 为每个机构维护成员档案 `sts_org_member`，关联已有 `us_sys_user`，不复制姓名、主部门或角色。

#### Scenario: 创建成员

- GIVEN 用户主部门属于目标机构
- WHEN 管理员创建成员并指定 member_type
- THEN 系统写入机构内唯一 `(organization_id, user_id)` 成员记录

#### Scenario: 工号唯一

- GIVEN 同机构已有非空 employee_no
- WHEN 再创建相同工号成员
- THEN 系统拒绝并返回业务冲突

### Requirement: 标签与岗位

系统 SHALL 支持机构内标签、岗位 CRUD，以及成员与标签/岗位关联；每成员至多一个主岗位。

#### Scenario: 删除保护

- GIVEN 标签或岗位仍被成员引用
- WHEN 管理员删除该标签或岗位
- THEN 系统拒绝删除

### Requirement: 角色数据范围

系统 SHALL 在 `us_sys_role` 保存机构归属与 `data_scope`（ALL/ORGANIZATION/DEPT/DEPT_AND_CHILDREN/CUSTOM/SELF）；CUSTOM 时使用
`us_sys_role_dept`。

#### Scenario: 非 CUSTOM 清理

- GIVEN 角色原为 CUSTOM 且有部门关联
- WHEN 更新为非 CUSTOM
- THEN 系统删除全部 `us_sys_role_dept` 行

#### Scenario: 范围并集

- GIVEN 用户多个角色均授予同一功能权限且 data_scope 不同
- WHEN 解析数据范围
- THEN 系统取这些角色范围的并集；未授予该权限的角色不贡献范围

### Requirement: superAdmin 特权

当账号有效且 `user_code` 精确等于 `superAdmin` 时，系统 SHALL 绕过功能权限与数据范围；该特权不依赖角色记录。

## 非目标

- 不修改已交付的素材库与课程业务表、API 契约语义（除后续自然接入 organization/dataScope 过滤）。
- 不实现参与范围、资源授权、监管关系。
