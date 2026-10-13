## ADDED Requirements

### Requirement: 后端动态菜单展示树必须与当前前端路由树一致

系统 MUST 以前端确认的菜单树定义 D/M/C 节点的层级、路由、组件、图标与顺序，并将其转换为后端稳定 ID 的扁平菜单数据。

#### Scenario: 新库初始化菜单

- **WHEN** 初始化服务读取 `menus.json`
- **THEN** 生成的 D/M/C 菜单 MUST 与当前前端菜单树一致
- **AND** 每个节点 MUST 具有稳定 ID、有效父节点和正确模块归属

### Requirement: 展示树调整不得丢失真实后端权限

菜单展示树重排时，系统 MUST 保留全部 Controller 授权点并将 F 权限挂到最接近的业务节点。

#### Scenario: 前端菜单未列出某个后端权限

- **WHEN** 前端菜单文件缺少一个真实 Controller 权限
- **THEN** 转换结果 MUST 保留该 F 权限
- **AND** 不得因展示页面缺失而删除普通角色可分配的后端能力

### Requirement: 新库与存量库菜单语义必须一致

`menus.json` 与正式 migration MUST 产生等价的菜单树和默认管理员授权，并尽量保持自定义角色已有权限授权。

#### Scenario: 存量数据库升级

- **WHEN** 菜单重排 migration 应用于已有数据库
- **THEN** 复用节点 MUST 更新为新层级和路由
- **AND** 废弃展示节点及其无效关联 MUST 被安全移除
- **AND** 仍有效的 F 权限 ID 和已有自定义角色授权 MUST 保持有效
