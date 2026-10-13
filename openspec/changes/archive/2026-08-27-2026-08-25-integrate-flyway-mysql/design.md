# Design：init 完整 SQL 与 Admin 增量

> 状态：已于 2026-08-25 经人工确认并完成实施。

## 1. 目录与所有权

- `backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql`：init 的完整结构真相源，按
  system、org、course、asset、question、exam、training、evaluation、survey、archive、certificate、ops、regulation 拆分。
- `backend/sts-service-admin/src/main/resources/db/migration/V*.sql`：Admin Flyway 的增量真相源。
- 仓库根 `database/` 删除，不保留 `B` Baseline Migration，也不复制第二套 SQL。

## 2. init 执行流程

1. 确认目标 MySQL 是空库。
2. 按固定模块顺序执行全部 `schema-*.sql`。
3. 使用 Flyway `baseline()` 将 `sts.init.schema-version` 写入 `flyway_schema_history`。
4. 写入角色、菜单、用户、字典和配置种子。
5. CLI 退出，部署流程不得再次运行。

完整 SQL必须可直接创建最新最终结构，不得使用 `ALTER TABLE` 或历史数据转换语句。init 禁用 Spring Boot 自动 `migrate`，只借助
Flyway API登记 baseline。

## 3. Admin 执行流程

- Admin 运行包只打包 `backend/sts-service-admin/src/main/resources/db/migration/V*.sql`。
- `baseline-on-migrate=false`、`out-of-order=false`、`clean-disabled=true`。
- Admin 启动时校验 history、命名和 checksum，只执行 baseline 版本之后 pending 的 `V`。
- 未经 init 的空库或未知非空库均不属于 Admin 自动接管范围。

## 4. 后续变更规则

同一次数据库变更必须完成三项：

1. 更新受影响的 init 模块完整 SQL为最新最终结构。
2. 新增 `V<version>__description.sql` 表达已有数据库增量。
3. 更新 `sts.init.schema-version=<version>`。

完整 SQL是持续更新的安装快照；已在共享环境执行的 `V` 不可修改、重命名或删除。空库 init 路径与上一版本 + `V` 路径必须得到相同最终结构。

## 5. 验证

- 静态检查全部 init SQL 不含 `ALTER TABLE`，模块文件齐全。
- MySQL 空库执行 13 个完整模块 SQL、baseline 和种子。
- Admin 在该 history 上 migrate/validate，当前执行数为 0。
- Admin 完整 Mapper/约束 IT 使用同一批 init SQL建库，再验证增量接管。
