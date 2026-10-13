# init 模块化完整 SQL 与 Admin Flyway 增量设计

> 状态：已于 2026-08-25 实施并通过 MySQL 集成测试。
>
> 实施依据：`openspec/changes/2026-08-25-integrate-flyway-mysql/`。

## 1. 最终职责

| 组件                       | SQL                                                                     | 职责                                    |
|--------------------------|-------------------------------------------------------------------------|---------------------------------------|
| `sts-service-admin-init` | `backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql` | 仅对空库执行一次完整建库、登记 baseline、写种子后退出       |
| `sts-service-admin`      | `backend/sts-service-admin/src/main/resources/db/migration/V*.sql`      | 长期运行，启动时校验并执行 baseline 之后 pending 的增量 |

仓库根 `database/` 已删除。不使用 Flyway `B` Baseline Migration，也不在两个模块中复制 SQL 源文件。

## 2. init 完整 SQL

完整 SQL按业务模块拆分：

```text
backend/sts-service-admin-init/src/main/resources/schema/
├── schema-system.sql
├── schema-org.sql
├── schema-course.sql
├── schema-asset.sql
├── schema-question.sql
├── schema-exam.sql
├── schema-training.sql
├── schema-evaluation.sql
├── schema-survey.sql
├── schema-archive.sql
├── schema-certificate.sql
├── schema-ops.sql
└── schema-regulation.sql
```

这些文件是当前版本的完整结构快照：使用最终 `CREATE TABLE`、索引和静态数据，禁止 `ALTER TABLE`
、历史回填和兼容转换。每次结构变化直接更新受影响模块，使全新数据库无需经历历史升级过程。

init 固定按 system → org → course → asset → question → exam → training → evaluation → survey → archive → certificate →
ops → regulation 执行，执行前确认数据库没有任何业务表。

## 3. Flyway baseline 交接

init 不让 Spring Boot 自动执行 Flyway migration。完整 SQL执行成功后，init 通过 Flyway API调用 `baseline()`，把
`sts.init.schema-version` 写入 `flyway_schema_history`，然后才写种子。

这样 Admin 首次启动时已经具有可信 history：

```text
空库
  → init 模块完整 SQL
  → Flyway baseline(schema-version)
  → init 种子
  → Admin Flyway pending V
```

`sts.init.schema-version` 必须是 14 位版本号，并与当前完整 SQL已包含的最高数据库变化一致。

## 4. Admin 增量

Admin 运行包只包含：

```text
backend/sts-service-admin/src/main/resources/db/migration/V<yyyyMMddHHmmss>__<description>.sql
```

配置保持：

- `validate-on-migrate=true`
- `validate-migration-naming=true`
- `fail-on-missing-locations=true`
- `baseline-on-migrate=false`
- `out-of-order=false`
- `clean-disabled=true`

未知非空数据库没有 init 建立的 history 时，Admin 必须启动失败，不能自动 baseline。

## 5. 后续数据库变更

每次变化必须作为一个原子交付完成：

1. 修改受影响的 `backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql`，直接表达新版本最终结构。
2. 新增 Admin `V<version>__description.sql`，表达上一版本到新版本的增量。
3. 将 `sts.init.schema-version` 更新为同一个 `version`。
4. 验证空库完整 SQL路径与上一版本 + `V` 路径得到相同 schema。

完整 SQL是持续更新的安装快照；已经在共享环境执行的 `V` 永久不可变，修正只能新增更高版本 `V`。

## 6. 构建与测试

- init 直接从模块标准资源目录打包 `schema/*.sql`，运行时读取 `classpath:schema/`。
- Admin 直接从模块标准 Flyway 目录打包 `db/migration/V*.sql`，无需额外 Maven 复制配置。
- Admin IT 测试资源复用 init 的 13 个完整 SQL，完成 baseline 后再启动 Admin Flyway。
- 自动测试检查模块文件齐全、完整 SQL不含 `ALTER TABLE`、Admin 包不含 `B` 或完整 SQL。

## 7. 当前基线

- schema version：`20260811150000`
- init 完整模块：13 个。
- Admin 当前增量：无；目录只保留编写规则说明，未来数据库首次变化时再新增第一个 `V`。
- 因系统尚未上线，人员选择简化等开发期变化已全部吸收到模块化完整 SQL；旧库转换脚本已删除，可从 Git 历史恢复。
