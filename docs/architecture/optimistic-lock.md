# 乐观锁契约（MyBatis-Plus 3.5.15 `OptimisticLockerInnerInterceptor`）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **依赖**：`../../architecture/stack.md`（MyBatis-Plus 3.5.15）、`logic-delete.md`（逻辑删除继承链）、[
`mapstruct.md`](./mapstruct.md)（属性覆盖规约）。  
> **适用范围**：`sts-service-admin` 模块下有增删改查的业务实体（排除关联中间表、日志表）。  
> **参考**：[MyBatis-Plus 乐观锁插件文档](https://baomidou.com/plugins/optimistic-locker/)

---

## 1. 设计决策

使用 MyBatis-Plus 官方 `OptimisticLockerInnerInterceptor` 拦截器 + `@Version` 注解实现乐观锁，配合自定义 `AppBaseMapper`
实现受影响行数校验。

| 决策项             | 选择                                                                  | 原因                                   |
|-----------------|---------------------------------------------------------------------|--------------------------------------|
| 乐观锁机制           | `OptimisticLockerInnerInterceptor` 拦截器                              | MyBatis-Plus 官方插件，全自动 SQL 改写，零业务代码侵入 |
| 版本字段            | `version`（`INT`，初值 `0`，每次更新 +1）                                     | 拦截器支持 int/long/Date/LocalDateTime    |
| 冲突检测            | 拦截器自动 `WHERE version = oldVersion`；`useAffectedRows=true` 返回 0 行即冲突 | 利用数据库行锁实现原子性冲突检测                     |
| 冲突异常            | `AppBaseMapper` 包装抛出 HTTP 409                                       | 统一业务反馈，前端根据 409 提示用户刷新               |
| `oldVersion` 来源 | `entity.version` — **必须由前端请求回传**                                    | 拦截器从 `entity` 对象读取当前版本号作为更新条件        |

---

## 2. 拦截器原理

`OptimisticLockerInnerInterceptor` 对 UPDATE 语句做以下自动改写：

```sql
-- 1. 从 entity 对象读取 @Version 字段值 (假设为 5)
-- 2. 执行改写后的 SQL
UPDATE us_sys_user
SET user_name=?,
    status=?,
    version=6 -- 自动计算 newVersion = 5+1
WHERE user_id=? AND deleted=0 AND version=5    -- 自动追加 version=5 校验条件
```

> **关键配置**：必须在 JDBC URL 中配置 `useAffectedRows=true`。若 version 不匹配，MySQL 返回 affected rows = 0，从而触发冲突逻辑。

---

## 3. 基准配置

### 3.1 注册拦截器
**类**：`com.sts.admin.config.MybatisPlusConfig`

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor()); // 注册乐观锁拦截器
    return interceptor;
}
```

### 3.2 实体类基类

`version` 定义在全功能业务实体基类 `BaseEntity` 上（**基类体系唯一真理源**见 [`logic-delete.md`](./logic-delete.md) §3.1，
本文不另立基类名）。A 类业务实体表继承 `BaseEntity` 即自动获得 `@Version`：

```java
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseEntity extends BaseAuditEntity {
    // updateBy / updateTime / deleted(@TableLogic) 见 logic-delete.md §3.1
    @Version
    private Integer version; // 乐观锁版本号
}
```

---

## 4. 自定义 Mapper 增强：`AppBaseMapper<T>`

所有业务 Mapper 必须扩展 `AppBaseMapper<T>`，以获得带冲突校验的更新能力。

**接口**：`com.sts.admin.mapper.AppBaseMapper`

```java
public interface AppBaseMapper<T> extends BaseMapper<T> {

    /**
     * 带乐观锁校验的更新：若受影响行数 <= 0，直接抛出 409 冲突异常
     */
    default int updateByIdWithVersionCheck(T entity) {
        int rows = this.updateById(entity);
        if (rows <= 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 2040, "数据已被他人修改，请刷新后重试");
        }
        return rows;
    }

    // 同样提供 updateWithVersionCheck 和 insertOrUpdateWithVersionCheck ...
}
```

---

## 5. 全链路标准流程（Service 层）

**核心原则：Service 层显式将前端 `version` 注入实体，利用 MapStruct 覆盖业务字段（排除 version），最后调用 Mapper 校验方法。**

### 5.1 标准 update 代码模板

```java
@Override
@Transactional
public void updateMenu(Long menuId, MenuUpdateRequest request) {
    // 1. 获取 DB 最新实体
    SysMenu menu = requireMenu(menuId);
    
    // 2. 显式设置前端传来的版本号（作为乐观锁 WHERE 条件）
    menu.setVersion(request.getVersion());
    
    // 3. MapStruct 属性覆盖（注意：Converter 必须配置 ignore version，参见 mapstruct.md）
    menuConverter.updateEntity(menu, request);
    
    // 4. 业务校验与额外处理...
    validateMenuType(request.getMenuType());

    // 5. 执行乐观锁更新，冲突自动抛出 409
    menuMapper.updateByIdWithVersionCheck(menu);
}
```


```text
User A (version: 3)         User B (version: 3)
      │                           │
      │ 提交更新 (v=3)             │
      ├───────────────────────────┤
      │ DB 成功：version 变为 4      │
      │                           │
      │                           │ 提交更新 (v=3)
      │                           ├───────────────────────────┐
      │                           │ DB 失败：WHERE version=3 匹配 0 行
      │                           │ Mapper 抛出 HTTP 409 异常  <──冲突拦截成功
```

---

## 6. 乐观锁排除原则（不参与乐观锁的 Entity）

为了保持系统简洁，并非所有表都需要乐观锁。以下两类场景**强制排除**乐观锁机制，后续新增表需遵循此逻辑：

| 场景分类        | 排除理由                                                                   | 示例                           |
|-------------|------------------------------------------------------------------------|------------------------------|
| **关联中间表**   | 仅用于建立 A-B 关系（如 `userId`-`roleId`），通常采用“先全删再全插”或简单幂等写入模式，不存在复杂的并发属性写竞争。 | `SysUserRole`, `SysRoleMenu` |
| **日志/审计类表** | 业务逻辑上属于“只增不改”的数据流，一旦写入即成为历史，不存在多人同时修改同一行数据的并发场景。                       | `SysLoginLog`, `SysOperLog`  |

> **判定标准**：若一张表没有复杂的“读-改-写”业务逻辑，继承 `BaseAuditEntity`（而非 `BaseEntity`），则无需在 DDL 中添加
`version` 字段。

---

## 7. 交付清单

- [x] `AppBaseMapper` 提供 `updateByIdWithVersionCheck` 包装方法。
- [x] Service 层 update 显式执行 `entity.setVersion(request.getVersion())`。
- [x] MapStruct Converter 在 `updateEntity` 方法上配置 `@Mapping(target = "version", ignore = true)`。
- [x] JDBC URL 包含 `useAffectedRows=true`。
- [x] 前端提交的 DTO 和查询返回的 VO 均包含 `version` 字段。
