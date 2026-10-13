# MapStruct 对象映射契约（项目级 · 1.6.3 · Spring 模式）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **依赖**：`../architecture/stack.md`（MapStruct 1.6.3）、[`optimistic-lock.md`](./optimistic-lock.md)（乐观锁规约）。  
> **适用范围**：`sts-service-admin` 模块下所有 Entity↔VO、DTO→Entity 的互转。  

---

## 1. 核心原则

**项目内所有对象转换一律使用 MapStruct Converter，禁止手写 `new XxxVO()` + `setXxx()` 或 `new XxxEntity()` + `setXxx()`。
**

| 转换方向             | 方式 | 方法签名                                                        |
|------------------|----|-------------------------------------------------------------|
| Entity → VO      | 映射 | `XxxVO toVO(Entity e)`                                      |
| DTO → Entity（新增） | 映射 | `Entity toEntity(CreateDto dto)`                            |
| DTO → Entity（修改） | 覆盖 | `void updateEntity(@MappingTarget Entity e, UpdateDto dto)` |

---

## 2. 设计决策

| 决策项   | 选择                          | 原因                                                   |
|-------|-----------------------------|------------------------------------------------------|
| 运行模式  | `componentModel = "spring"` | 生成 `@Component` Bean，支持 Spring 依赖注入                  |
| 未映射处理 | `ReportingPolicy.IGNORE`    | 仅对同名且需排除的字段（如 `version`）显式 ignore，简化配置               |
| 乐观锁处理 | **显式排除 version**            | 乐观锁 `version` 由 Service 层基于业务上下文显式控制，不通过 Mapper 自动覆盖 |

---

## 3. Converter 标准模板

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysXxxConverter {

    // ======================== Entity → VO ========================
    XxxVO toVO(SysXxx entity);

    // ======================== DTO → Entity ========================
    SysXxx toEntity(XxxCreateRequest request);

    /**
     * 修改时覆盖属性。必须 ignore version，由 Service 显式设置以支持乐观锁。
     */
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget SysXxx entity, XxxUpdateRequest request);
}
```

---

## 4. 全链路 Service 模式

结合乐观锁的 3 步标准更新模式：

```java
@Override
@Transactional
public void updateXxx(Long id, XxxUpdateRequest request) {
    // 1. 获取 DB 原始实体
    SysXxx entity = requireXxx(id);
    
    // 2. 注入前端版本号（用于乐观锁校验）
    entity.setVersion(request.getVersion());
    
    // 3. MapStruct 覆盖除 version 外的业务字段
    xxxConverter.updateEntity(entity, request);
    
    // 4. 调用 Mapper 校验方法执行更新
    xxxMapper.updateByIdWithVersionCheck(entity);
}
```

---

## 5. 特殊场景处理

### 5.1 关联字段与 Tree 节点

Converter 仅处理平铺的基础字段映射。关联主键集合（如 `roleIds`）或树形结构（如 `children`）在 Service 层后设，Converter 侧需配置
`@Mapping(target = "...", ignore = true)`。

### 5.2 字段排除

修改时某些不允许更改的字段（如 `configKey`），若 DTO 中不包含该字段，MapStruct 默认不会覆盖，无需额外声明 ignore。

---

## 6. Audit 自检清单

- [x] 所有 `@Mapper` 标记 `componentModel = "spring"`。
- [x] `updateEntity` 方法显式标记 `@Mapping(target = "version", ignore = true)`。
- [x] Service 中不再出现 `new` 实体或 VO 的样板代码，全部通过注入的 Converter 转换。
- [x] 编译生成的实现类位于 `build/generated/sources/annotationProcessor/`。
