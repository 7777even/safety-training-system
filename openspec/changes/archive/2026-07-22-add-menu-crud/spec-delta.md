# Spec Delta：系统菜单管理

## ADDED

### 菜单维护 API

系统必须提供以下受权限保护的菜单维护接口：

- `POST /api/system/menus`，权限 `sys:menu:add`，新增菜单并返回菜单 ID；
- `PUT /api/system/menus/{menuId}`，权限 `sys:menu:edit`，以乐观锁版本号修改菜单；
- `DELETE /api/system/menus/{menuId}`，权限 `sys:menu:delete`，逻辑删除菜单。

请求模型必须描述菜单父级、名称、路由、组件、权限标识、图标、排序、可见性、类型和所属模块。`menuType` 仅允许 `D`、`M`、`C`、`F`，
`isVisible` 仅允许 `0`、`1`。

### 菜单树完整性

系统必须拒绝引用不存在父菜单的新增或修改请求，拒绝将菜单设为自身父级或移动到其后代节点下。

暂定：删除存在直接子菜单或角色菜单关联的菜单时，系统返回冲突错误；不自动级联删除子菜单或解除角色关联。

### 菜单 CRUD 质量验证

菜单 CRUD 必须同时具备不依赖数据库的自动化测试和基于 Testcontainers MySQL 的集成测试。前者验证请求/业务校验，后者验证真实持久化、逻辑删除和关联约束。
