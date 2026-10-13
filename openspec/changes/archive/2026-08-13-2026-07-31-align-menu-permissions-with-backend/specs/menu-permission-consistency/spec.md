## ADDED Requirements

### Requirement: 后端权限点必须可通过 RBAC 菜单目录分配

系统 MUST 为每个 Controller `@SaCheckPermission` 权限点提供至少一个菜单或按钮权限记录，使非特权角色可按职责获得真实后端能力。

#### Scenario: 新增后端权限点

- **WHEN** Controller 新增一个 `@SaCheckPermission` 权限点
- **THEN** 同一已确认 Change MUST 同步菜单种子和存量数据库 migration
- **AND** 权限覆盖自动化测试 MUST 通过

### Requirement: 菜单权限不得声明不存在的后端能力

系统 MUST 拒绝在权限目录中保留没有真实后端授权点的功能权限，除非存在已确认的兼容性例外。

#### Scenario: 权限目录存在无后端能力的按钮

- **WHEN** 自动校验发现非空菜单权限没有对应后端授权点
- **THEN** 校验 MUST 失败
- **AND** 实施方 MUST 删除该占位权限或提交人工确认的兼容性例外

### Requirement: 新库与存量库权限目录必须一致

`menus.json` 与正式 migration MUST 对新增、删除的权限记录保持一致；默认 `admin` MUST 获得新增权限，自定义角色 MUST 不被自动扩权，
`superAdmin` MUST 继续使用硬编码特权旁路。

#### Scenario: 存量数据库升级

- **WHEN** migration 应用于已有系统数据库
- **THEN** 28 个新增权限 MUST 写入菜单表并绑定默认 `admin`
- **AND** 2 个无效占位权限及其角色关联 MUST 被移除
- **AND** 其他自定义角色授权 MUST 保持不变
