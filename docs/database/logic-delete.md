# MyBatis-Plus 逻辑删除与唯一索引冲突解决方案

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

本平台采用 **`deleted = 主键 ID`** 的方案，解决逻辑删除后无法创建同名记录的问题。

## 1. 核心设计

- **未删除状态**：`deleted = 0`
- **已删除状态**：`deleted = 主键 ID`

通过将 `deleted` 纳入唯一索引（联合索引），确保：

1. 在线记录（`deleted=0`）全局唯一。
2. 已删除记录由于 `deleted` 值为各自的主键 ID，彼此之间及与在线记录之间均不冲突。

## 2. DDL 规范

受影响的表（存在业务唯一标识）：

- `us_sys_user` (`user_code`, `deleted`)
- `us_sys_role` (`role_code`, `deleted`)
- `us_sys_dict_type` (`dict_type`, `deleted`)
- `us_sys_config` (`config_key`, `deleted`)
- `us_sys_auth_identity` (`identity_type`, `identifier`, `deleted`)

其他无唯一索引的表（如 `us_sys_dept`, `us_sys_menu`）统一将 `deleted` 字段改为 `BIGINT` 类型以保持一致。

## 3. 后端实现

### 3.1 实体基类体系（**唯一真理源**）

> 本节是全项目**实体基类命名与字段的唯一真理源**。`ddl-conventions.md`（DDL 分类 §2.1）、`optimistic-lock.md`
> 只**引用**这两个名字，不另立 `LogicDeleteBaseEntity` / `VersionedLogicDeleteBaseEntity` 等别名。

全项目**只有两个**实体基类，按"**该行是否会被就地修改**"二选一：

| 基类                                       | 包含字段                                                                                     | 删除行为                        | 适用（DDL 分类见 `ddl-conventions.md` §2.1）        |
|:-----------------------------------------|:-----------------------------------------------------------------------------------------|:----------------------------|:---------------------------------------------|
| `BaseAuditEntity`                        | `createBy`, `createTime`                                                                 | **物理删除**（框架默认）              | **B 关联中间表**（解绑=真删）、**C 日志/流水表**（只增不删）——不可变记录 |
| `BaseEntity` (extends `BaseAuditEntity`) | + `updateBy`, `updateTime`, `deleted`(Long,`@TableLogic`), `version`(Integer,`@Version`) | **逻辑删除**（`deleted=ID`）+ 乐观锁 | **A 业务实体表**——可被就地修改                          |

> - **`update_*` 只在 `BaseEntity`**：只有会被就地修改的 A 类才有"更新"语义；中间表（建立/解绑）、日志表（只增）都无就地修改，故不含
    `update_*`。
> - `BaseServiceImpl.removeById` 以 `BaseEntity.class.isAssignableFrom(entityClass)` 分流：`true`→软删（`deleted=ID`），
    `false`→物理删。故 B/C 继承 `BaseAuditEntity` 自动走物理删。

**基类声明：**
```java
// 不可变记录基类（B 关联中间表 / C 日志流水表）——仅创建审计，物理删除
@Data
public abstract class BaseAuditEntity {
    @TableField(fill = FieldFill.INSERT) private Long createBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}

// 全功能业务实体基类（A）——审计 + 逻辑删除 + 乐观锁
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseEntity extends BaseAuditEntity {
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "0") private Long deleted;   // 实际删除值(主键ID)由 BaseServiceImpl.removeById 注入
    @Version                                  private Integer version;
}
```

### 3.2 Service 实现

全项目 ServiceImpl 统一继承 `com.sts.common.service.BaseServiceImpl<M, T>`。该基类自动处理删除逻辑：

1. **逻辑删除分支**：若实体类继承自 `BaseEntity`，`removeById` 自动执行 `UPDATE ... SET deleted = {ID} WHERE ...`。
2. **物理删除分支**：若实体类仅继承 `BaseAuditEntity` 或其他，执行 MyBatis-Plus 默认删除行为（通常为物理删除）。

**核心代码逻辑：**
```java
@Override
public boolean removeById(Serializable id) {
    if (BaseEntity.class.isAssignableFrom(getEntityClass())) {
        // 自动注入 deleted = ID
        TableInfo tableInfo = TableInfoHelper.getTableInfo(getEntityClass());
        return update(Wrappers.<T>update().setSql("deleted = " + id)...);
    }
    return super.removeById(id);
}
```

### 3.3 铁律（约束）

1. **Service 层调用**：业务代码中严禁调用 `xxxMapper.deleteById(id)`，必须调用 `this.removeById(id)` 以触发基类中的 ID
   注入逻辑。
2. **不可变记录隔离**：中间关联表（如 `us_sys_user_role`）与日志/流水表（如 `us_sys_oper_log`）继承 `BaseAuditEntity`（仅
   `create_*`），确保删除行为是物理删除、且不带 `update_*`/`deleted`/`version` 死重量。
