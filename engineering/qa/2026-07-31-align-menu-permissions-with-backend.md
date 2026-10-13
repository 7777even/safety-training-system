# QA：菜单权限与后端授权点对齐

日期：2026-07-31  
OpenSpec：`2026-07-31-align-menu-permissions-with-backend` Task 1  
Owner：lims

## 验收范围

- 后端 223 个 `@SaCheckPermission` 权限与 `menus.json` 双向一致。
- 菜单 ID、父节点和模块归属完整。
- 28 个新增权限与正式 migration 数据一致。
- 删除 `sys:operlog:export`、`sys:file:download` 两个无后端接口的占位权限。
- 默认 `admin` 获得新增权限；其他自定义角色不自动扩权。

## TDD 记录

1. Red：新增 `MenuPermissionConsistencyTest` 后运行目标测试，观察到 28 个权限缺失、2 个无效占位导致失败。
2. Green：更新 `menus.json` 和 migration 后，同一测试通过。
3. Refactor：增加 migration 与 JSON 字段级同源校验，保持 28 个固定 ID 和删除语义可回归。

## 验证结果

| 验证                 | 命令                                                                                                                               | 结果                                             |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| init 单测            | `mvn -pl sts-service-admin-init -am test`                                                                                        | 7/7 通过                                         |
| Admin 零 DB         | `mvn -pl sts-service-admin -am test`                                                                                             | 429/429 通过                                     |
| migration MySQL IT | `mvn -pl sts-service-admin -am -Pintegration-test -Dtest=MenuPermissionMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test` | 未执行到用例：环境无可用 Docker socket，Testcontainers 启动失败 |
| OpenSpec           | `openspec validate 2026-07-31-align-menu-permissions-with-backend --strict`                                                      | 通过                                             |
| 差异格式               | `git diff --check`                                                                                                               | 通过                                             |

## Review

### 规格符合性

结论：通过。

- 补齐数量、所属模块、移除项和角色授权策略均符合已确认 Task。
- 未修改 Controller、OpenAPI、前端、权限模型或数据库结构。
- migration 与空库菜单种子同步。

### 代码质量

结论：通过。

- 一致性测试覆盖权限双向集合、菜单树结构及 migration/JSON 同源。
- migration 可重复执行；仅默认 `admin` 自动获得新增权限。
- 未引入生产依赖或无关重构。

## 未运行项

`MenuPermissionMigrationIT` 已实现，但当前环境没有可用 Docker socket，无法执行真实 MySQL 验证。不能用零 DB 测试替代该结论；Docker
可用后应重跑上述 IT 命令。
