# Flyway Versioned Migrations

最新最终结构由 `sts-service-admin-init` 的模块化完整 SQL 覆盖；本目录保存 Admin 从既有版本向前升级的增量 SQL。

后续已确认的数据库变更必须在这里新增：

`V<yyyyMMddHHmmss>__<lower_snake_case_description>.sql`

已经在共享环境执行的 `V` 不得修改、重命名或删除。
