# QA：菜单展示树与当前前端路由树对齐

日期：2026-08-01  
OpenSpec：`2026-08-01-align-menu-tree-with-current-frontend` Task 1  
Owner：lims

## 验收范围

- 39 个 D/M/C 展示节点与 `current-online-menu-tree.json` 的层级和字段逐项一致。
- 后端 223 个 `@SaCheckPermission` 权限全部保留为稳定 F 节点。
- `menus.json` 为初始化服务可消费的扁平树，ID、父节点和模块归属有效。
- migration 幂等同步存量种子菜单，不删除或授权非种子的自定义菜单。
- 复用已有 F 权限 ID，保持自定义角色已有功能授权。

## TDD 记录

1. Red：新增展示树一致性测试，旧 `menus.json` 因目录、路由、组件和顺序不一致失败。
2. Green：转换菜单后，39 个展示节点及 223 个权限点校验通过。
3. Refactor：将 migration 删除范围由全表排除收紧为 28 个明确淘汰的旧种子 ID，并限制默认 `admin` 只绑定本次 262 个种子节点。

## 验证结果

| 验证                 | 命令                                                                            | 结果                                      |
|--------------------|-------------------------------------------------------------------------------|-----------------------------------------|
| init 单测            | `mvn -pl sts-service-admin-init -am test`                                     | 10/10 通过；菜单一致性 5/5                      |
| Admin 零 DB         | `mvn -pl sts-service-admin -am test`                                          | 429/429 通过                              |
| migration MySQL IT | `mvn -pl sts-service-admin -am -Pintegration-test test`                       | 环境无可用 Docker socket，105 个 IT 均未执行到数据库断言 |
| IT 编译              | `mvn -pl sts-service-admin -am -DskipTests test`                              | 通过，含 `MenuTreeAlignmentMigrationIT`     |
| OpenSpec           | `openspec validate 2026-08-01-align-menu-tree-with-current-frontend --strict` | 通过                                      |
| 差异格式               | `git diff --check`                                                            | 通过                                      |

## Review

### 规格符合性

结论：通过。

- 前端输入完整决定展示节点，未修改前端文件内容。
- 权限集合仍与 Controller 双向一致，没有改变权限字符串或 API。
- 新库种子与存量 migration 使用同一组 262 个目标节点。

### 代码质量

结论：通过。

- 测试覆盖展示树字段、权限全集、ID/父节点/moduleId 及 migration/seed 同源。
- migration 只删除明确的旧种子节点，保留自定义菜单及角色关系。
- 默认 `admin` 授权限定为目标种子 ID，不会顺带获得自定义菜单。
- 未引入生产依赖或无关重构。

## 未运行项

真实 MySQL migration IT 因当前环境没有 Docker socket 无法执行，不能用零 DB 结果替代。Docker 可用后应重跑
`MenuTreeAlignmentMigrationIT` 或完整 integration-test profile。
