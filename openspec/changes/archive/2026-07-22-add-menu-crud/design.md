# Design：系统菜单管理 CRUD

## 系统设计

在 `SysMenuController`、`SysMenuService` 和 `SysMenuServiceImpl` 中扩展 CRUD。实体 `SysMenu` 与表 `us_sys_menu`
已包含所有持久化字段和 `version` 乐观锁字段，因此不调整数据库结构。

## API 设计

| 能力   | URL / Method                        | 权限                | 成功 data |
|------|-------------------------------------|-------------------|---------|
| 新增菜单 | `POST /api/system/menus`            | `sys:menu:add`    | 新菜单 ID  |
| 修改菜单 | `PUT /api/system/menus/{menuId}`    | `sys:menu:edit`   | `null`  |
| 删除菜单 | `DELETE /api/system/menus/{menuId}` | `sys:menu:delete` | `null`  |

所有 Controller 裸返回值均由 `ResultEasyTransAdvice` 包装为 `{ "code": 0, "message": "success", "data": ... }`。

### 请求模型

- `MenuCreateRequest`：`parentId`、`menuName`、`path`、`component`、`perms`、`icon`、`sortOrder`、`isVisible`、`menuType`、
  `moduleId`。
- `MenuUpdateRequest`：同上并增加必填 `version`；菜单 ID 由路径参数提供。
- `menuType` 仅允许表结构已定义的 `D`、`M`、`C`、`F`。
- `isVisible` 仅允许 `0`（隐藏）或 `1`（显示）。

## 业务校验

- 创建或修改为非根菜单时，父菜单必须存在。
- 修改时父菜单不得为自身，且不得移动到当前菜单的后代节点下。
- 保留既有可见性规则：父级隐藏时不能单独显示子级；隐藏父级会递归隐藏子级。
- 暂定：删除前若存在直接子菜单，返回 `409` 业务错误；若存在 `us_sys_role_menu` 关联，也返回 `409` 业务错误。不得自行级联删除或解绑。
- 修改使用 `version` 调用现有乐观锁更新方法。

## 数据模型

- 复用 `us_sys_menu`、`SysMenu` 与其逻辑删除能力。
- 删除菜单使用 `BaseServiceImpl.removeById` 的逻辑删除语义。
- 删除约束查询复用 `SysMenuMapper`，并新增对 `SysRoleMenuMapper` 的关联计数查询；不修改任何表或迁移。

## 权限设计

- 新增、编辑、删除分别复用 `sys:menu:add`、`sys:menu:edit`、`sys:menu:delete`。
- 每个写操作增加 `@OperLog`，业务类型分别为 INSERT、UPDATE、DELETE。

## 测试设计

- 无数据库：Mockito Service 单元测试覆盖父级不存在、自身/后代循环、成功创建、乐观锁更新、删除阻断与成功删除；Controller
  单元测试覆盖路径/请求体透传和 `@Valid`。
- 带数据库：Testcontainers MySQL 集成测试验证菜单插入、更新乐观锁、逻辑删除及真实 `us_sys_role_menu` 关联阻断查询。

## 风险和取舍

- 菜单删除会影响授权和导航。采用“先阻断再人工处理依赖”的暂定策略可避免无意撤销角色权限；需开发人员确认。
- 不将 `path`、`component`、`perms` 设为 DTO 必填项，因为表结构允许它们为空，且 D/M/C/F 不同类型的字段要求尚未有确认的业务规则。
