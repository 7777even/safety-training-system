# MySQL DDL 规约

> 适用范围：系统管理及后续业务表。语法级铁律；具体完整结构见
`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql`。
> 逻辑删除实现见 [logic-delete.md](./logic-delete.md)。

## SQL 落盘

1. **完整 DDL 真相源**：`backend/sts-service-admin-init/src/main/resources/schema/schema-*.sql`，按业务模块拆分，只包含最新最终结构，禁止
   `ALTER TABLE`。
2. **增量变更真相源**：`backend/sts-service-admin/src/main/resources/db/migration/V<version>__description.sql`
   ，只表达已有数据库的本次升级。
3. init 对空库执行完整模块 SQL并登记 `sts.init.schema-version`；Admin Flyway 只打包和执行 `V`。
4. 每次数据库变化必须同时更新受影响的完整模块 SQL、新增 `V` 并更新 schema version，两条路径最终结构必须一致。

## 1. 主键与业务标识

1. 所有表（含多对多中间表）使用单一 `BIGINT` 代理主键（自增）。
2. 禁止复合主键；中间表用独立主键 + `UNIQUE KEY` 表达业务唯一。
3. 禁止用 UUID 字符串作主键。

### 1.1 id / code / name

| 角色 | 命名 | 性质 | 是否必需 |
|------|------|------|----------|
| 代理主键 id | `{entity}_id` | 业务无关 | 必有 |
| 业务码 code | 领域码 | 创建后不可变、唯一 | 可选 |
| 展示名 name | `{entity}_name` | 可变、仅展示 | 按需 |

- 引用一律用 id（内部）或 code（对外），绝不用 name。
- 不可变业务键禁止命名为 `name`。

### 1.2 常用列名

| 语义 | 统一列名 | 类型 |
|------|----------|------|
| 显示排序 | `sort_order` | int |
| 启用/禁用 | `status` | tinyint（0 禁用 / 1 正常） |
| 布尔标志 | `is_xxx` | tinyint（0/1） |
| 业务描述 | `description` | varchar/text（禁止 `remark`/`comments`） |

## 2. 表分类与审计字段

| 类别 | 场景 | 审计字段 | 实体基类 |
|------|------|----------|----------|
| A. 业务实体 | 可就地修改（用户/角色等） | create_by/time、update_by/time、deleted、version | `BaseEntity` |
| B. 关联中间表 | 只建立/解绑关系 | create_by、create_time | `BaseAuditEntity`（物理删，无 `@TableLogic`） |
| C. 日志/流水 | 只增不改 | create_by、create_time | `BaseAuditEntity` |

```sql
`create_by`   bigint   DEFAULT NULL COMMENT '创建者用户ID',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_by`   bigint   DEFAULT NULL COMMENT '更新者用户ID',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted`     bigint   NOT NULL DEFAULT 0 COMMENT '逻辑删除(0未删, 非0=主键ID)',
`version`     int      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'
```

红线：

1. `create_by` / `update_by` 存用户 ID，禁止存用户名。
2. `deleted` 为 bigint：未删=0，删除时写入主键 ID（配合唯一键软删再建）。
3. `version` 配合乐观锁拦截器。

## 3. 引擎与字符集

```sql
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='...';
```

脚本头：`SET NAMES utf8mb4;`

## 4. 新增表自检

- [ ] 单列 BIGINT 自增主键
- [ ] 按 A/B/C 补齐审计字段
- [ ] InnoDB + utf8mb4
- [ ] 中间表有 UNIQUE KEY
- [ ] Entity 继承正确基类（`com.sts.common.entity`）
