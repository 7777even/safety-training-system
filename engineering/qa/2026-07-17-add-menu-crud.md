# QA：系统菜单管理 CRUD

## 范围

OpenSpec Change：`2026-07-17-add-menu-crud`。

验证菜单新增、修改、删除接口、菜单树约束、逻辑删除、角色菜单关联删除护栏和 API 契约；不涉及数据库结构变更。

## 验证结果

| 检查项          | 结果 | 证据                                                                                                                                       |
|--------------|----|------------------------------------------------------------------------------------------------------------------------------------------|
| CRUD API     | 通过 | 新增 `POST`、`PUT /{menuId}`、`DELETE /{menuId}`，分别声明 `sys:menu:add/edit/delete`。                                                            |
| 参数与约束        | 通过 | DTO 校验父菜单、菜单名称、菜单类型 `D/M/C/F`、可见状态 `0/1` 和更新版本号。                                                                                         |
| 树完整性         | 通过 | Service 拒绝不存在父级、自身父级、移动到后代，以及将可见子项挂到隐藏父级。                                                                                                |
| 删除保护         | 通过 | Service 拒绝存在子菜单或 `us_sys_role_menu` 关联的删除请求。                                                                                             |
| API Contract | 通过 | OpenAPI 已包含三个操作、字段注释、请求示例和 `ResultEasyTransAdvice` 包装后的成功响应示例；总操作数为 55。                                                                  |
| 无数据库测试       | 通过 | `mvn -pl sts-service-admin -am test -Dtest=SysMenuControllerTest,SysMenuServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`：21 项通过。 |
| 真 MySQL 测试   | 通过 | `mvn -pl sts-service-admin -am test -Pintegration-test -Dtest=MenuCrudPersistenceIT -Dsurefire.failIfNoSpecifiedTests=false`：2 项通过。      |

## 结论

菜单管理 CRUD 满足已确认的“有子菜单或角色关联即拒绝删除”策略。测试使用 Testcontainers 临时 MySQL，不涉及生产数据库或迁移。
