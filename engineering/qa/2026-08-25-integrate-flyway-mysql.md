# init 完整建库与 Admin Flyway 增量 QA

## 范围

- init 使用模块内 `src/main/resources/schema/` 的 13 个最新完整 SQL建库，不执行增量。
- init 建库后登记 baseline，再写种子。
- Admin 从模块内标准 `src/main/resources/db/migration/` 读取未来新增的 `V`；当前没有增量 SQL。
- 删除根 `database/`、Flyway `B` 和上线前旧增量兼容资产。

## 自动化验证

| 验证                              | 结果                                                                                                                                                                                 |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FlywayMigrationResourcesTest`  | 1/1 通过；当前允许零增量，未来 migration SQL 只允许 `V`                                                                                                                                            |
| `DatabaseInitializerTest`       | 2/2 通过；完整 SQL → baseline → 种子顺序正确                                                                                                                                                  |
| `DatabaseSchemaInitializerTest` | 4/4 通过；模块顺序、资源齐全、无 `ALTER TABLE`                                                                                                                                                   |
| `AdminInitFlywayIT`             | 1/1 通过；13 个完整模块建库、baseline `20260811150000`、种子写入                                                                                                                                   |
| `FlywayMigrationIT`             | 2/2 通过；Admin 从 init baseline 接管、validate、未知非空库拒绝                                                                                                                                   |
| 受影响 Admin MySQL IT              | 6/6 通过                                                                                                                                                                             |
| Admin 完整零 DB                    | 668 个用例，0 失败、0 错误、1 跳过                                                                                                                                                             |
| Admin 完整 MySQL IT               | 185/185 通过；覆盖完整模块建库、synthetic baseline、当前零增量及既有持久化回归；已删除历史增量专用 IT                                                                                                                  |
| init 完整零 DB                     | 9 个用例中 7 个通过、2 个既有菜单种子一致性用例失败；本次新增初始化/Flyway 用例 6/6 通过                                                                                                                             |
| 打包与包内容                          | `mvn -pl sts-service-admin,sts-service-admin-init -am -DskipTests clean package` 通过；Admin 的 `db/migration/` 仅含 README、没有 SQL，init CLI 包仅含 13 个 `schema-*.sql`，均含 Flyway Core/MySQL |
| OpenSpec                        | `openspec validate 2026-08-25-integrate-flyway-mysql --strict` 通过                                                                                                                  |
| 文档与差异                           | `git diff --check` 通过；init 完整 SQL 静态检查无 `ALTER TABLE`                                                                                                                              |

## 已知非本次问题

init 既有 `MenuPermissionConsistencyTest` 仍有 3 个用例中 2 个失败：菜单 `9531` 重复，以及 `common:audience:*` 权限已无对应
Controller。它们属于菜单种子业务规则，不属于数据库初始化/Flyway 边界，本 Change 未越界修改。

## 结论

数据库初始化与增量交接链路、Admin 全量回归和打包资源边界均已通过。init 全套零 DB 仍受上述两个既有菜单种子一致性问题影响；该问题未影响本
Change 新增的 6 个零 DB 用例和 1 个真库 IT。
