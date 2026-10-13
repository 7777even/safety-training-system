# Design：菜单权限与后端真实授权点一致性

## 权限真相源与对齐原则

1. 后端可执行能力以 Controller 的 `@SaCheckPermission` 为真实授权点。
2. `menus.json` 为新建空库的菜单/按钮种子；正式 migration 为既有数据库的升级入口。
3. 每个后端权限点必须至少存在一个 `us_sys_menu.perms` 对应项。
4. 菜单权限不得指向不存在的后端能力；当前无接口支撑的 `sys:file:download`、`sys:operlog:export` 删除，不反向新增 API。
5. 页面菜单可与其“查询/列表”按钮复用同一权限，既有重复保持兼容；除明确允许的页面/按钮复用外，不新增重复权限。

## 缺失权限清单

| 业务域 | 数量 | 权限                                                                                                                                                                                             |
|-----|---:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 培训  | 11 | `training:attachment:add/delete/edit`、`training:attendance:confirm`、`training:learner:task`、`training:offline-score:list`、`training:result:list/synchronize`、`training:reward:add/delete/edit` |
| 问卷  |  4 | `survey:instance:add/bind/stats`、`survey:survey:delete`                                                                                                                                        |
| 评价  |  1 | `evaluation:record:add`                                                                                                                                                                        |
| 档案  |  2 | `archive:export:create/list`                                                                                                                                                                   |
| 证书  | 10 | `certificate:cert:list/reissue/revoke`、`certificate:rule:add/delete/list`、`certificate:template:add/delete/edit/publish`                                                                       |

## 菜单归属

- 培训执行类权限挂在现有“培训计划”菜单下。
- 问卷定义和投放权限挂在现有“问卷管理”菜单下。
- 评价记录新增权限挂在现有“评价记录”菜单下。
- 档案导出权限挂在现有“培训档案”菜单下。
- 证书模板、规则和证书操作权限挂在现有“证书管理”菜单下。
- 新增项使用未占用且稳定的固定 `menu_id`，`module_id` 沿用所属模块；不得修改既有菜单 ID。

## 存量数据库迁移

正式 migration 必须：

1. 幂等插入 28 个 `F` 类型菜单按钮，字段与 `menus.json` 完全一致。
2. 将新增菜单绑定给默认 `admin` 角色；不得为 `superAdmin` 建立角色或角色菜单记录。
3. 删除两个无真实能力的占位菜单以及相应 `us_sys_role_menu` 关联。
4. 不修改自定义角色的其他授权；新增权限不自动授予非 `admin` 角色。
5. 不变更任何表、索引或字段。

## 自动一致性校验

新增零 DB 测试扫描后端 Controller 源码或编译后注解，并读取 `menus.json`，验证：

- 所有 `@SaCheckPermission` 权限均被菜单覆盖。
- 菜单中所有非空权限均有后端注解，页面/按钮复用允许一对多。
- menu ID 唯一、父节点存在、根节点 `menu_id=module_id`。
- 新增项与 migration 的 ID、父节点、权限、类型和模块保持一致。

## API 与权限边界

本 Change 不改变 URL、HTTP Method、请求响应、错误码或 `docs/api/*.openapi.json`。它只修正 RBAC 可分配权限目录和默认管理员授权数据。

## 风险与取舍

- 删除两个占位权限可能影响依赖其显示按钮的调用方，但当前后端没有对应接口，保留会形成虚假授权能力，因此选择删除。
- migration 只默认授权 `admin`；自定义角色须由管理员按职责重新选择新增权限，避免权限扩张。
- 若实现时发现某菜单权限实际由非注解鉴权机制使用，属于权限语义冲突，必须暂停并标记“需人工决策”。
