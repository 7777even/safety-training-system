# 缓存管理（系统管理 · 子菜单）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **分类**：**开发功能点**（`./`）— 与用户管理、字典管理同级，**独立菜单页面**。  
> **后端详设**：`detailed-design.md` §2.2  
> **横切依赖**：L1 读路径 [id-name-cache.md](../id-name-cache.md)；字典目录见
[dict-catalog.md](./dict-catalog.md)

---

## 1. 业务定位

| 项    | 约定                                                                                |
|------|-----------------------------------------------------------------------------------|
| 所属模块 | **系统管理**（Metadata Config 分组下，与「数据字典」「全局参数」并列）                                     |
| 菜单名称 | **缓存管理**                                                                          |
| 目标用户 | 平台管理员 / 运维                                                                        |
| 能力   | **动态获取**固定缓存域注册表；按 **整库（ALL）** 或 **分条目（ENTRY）** 清理 L1；通过 **Redis Pub/Sub** 触发集群同步 |
| 范围   | **二级缓存管理态**：目前仅负责「失效指令转发与状态展示」，不直接存储/检索业务数据条目（Data-less L2）                       |
| 非目标  | 不配置 Caffeine 参数、不暴露 Redis 键浏览（无 L2 数据缓存）                                          |

---

## 2. 动态注册表设计 (Dynamic Registry)

为了保证前后端模型一致性，缓存管理台**禁止**手动维护静态列表，必须通过后端接口动态获取。

### 2.1 后端获取逻辑

- **数据源**：`com.sts.common.cache.CacheDomain` 枚举。
- **获取方式**：`CacheAdminService` 遍历枚举，封装为 `CacheRegistryItemVO`。
- **扩展性**：未来新增业务缓存（如 `DEVICE`、`ALARM`）只需在枚举中追加，管理后台自动上屏。

### 2.2 注册项字段说明

| 字段                  | 说明                    | 示例             |
|:--------------------|:----------------------|:---------------|
| `domain`            | 内部枚举名，用于提交 `clear` 请求 | `USER`         |
| `cacheName`         | Redis Pub/Sub 使用的标识符  | `user`         |
| `caffeineCacheName` | 本地 Caffeine 实例名称      | `display-user` |
| `relatedBusiness`   | 关联业务描述，用于页面展示         | `用户管理 CUD`     |

---

## 3. 菜单与权限（`us_sys_menu`）

Implement 须同步更新 init 种子和对应 Admin `V` 增量（路径可微调，权限串不可改）：

| 字段           | 值                                                      |
|--------------|--------------------------------------------------------|
| `menu_type`  | `C`（菜单）                                                |
| `menu_name`  | 缓存管理                                                   |
| `parent_id`  | 系统管理 → 全局配置 目录（与字典、参数同父级，Implement 查种子 `menu_name` 对齐） |
| `path`       | `cache`                                                |
| `component`  | `system/cache/index`                                   |
| `perms`      | `sys:cache:list`                                       |
| `icon`       | `redis` 或项目统一运维图标                                      |
| `sort_order` | 位于「数据字典」与「全局参数」之间（建议 `25`）                             |

**按钮权限（F）**：

| 按钮    | `perms`           | 说明                     |
|-------|-------------------|------------------------|
| 查询注册表 | `sys:cache:list`  | 进入页面、`GET registry`    |
| 执行清理  | `sys:cache:clear` | `POST clear`、表格行「清理整库」 |

前端路由建议：`/system/cache`（与 `layout.md` 动态路由规则一致）。

---

## 3. 页面交互摘要

完整 UI 见 **`page-design.md` §2.2**。要点：

- **表格**：`GET /api/v1/system/cache/registry` 返回的固定四行（USER / DEPT / ROLE / DICT_CATALOG）。
- **操作**：每行「清理整库」→ `scope=ALL`；「按条目清理」→ 弹窗填 `key` → `scope=ENTRY`。
- **确认**：`el-message-box` 二次确认；成功提示「已清理并广播集群」。
- **权限**：工具栏按钮 `v-permission="'sys:cache:clear'"`。

---

## 4. 固定缓存域（`CacheDomain`）

Implement **禁止**运行时动态注册。

| `CacheDomain`  | `cacheName`（Pub/Sub） | Caffeine 名     | 关联业务     |
|----------------|----------------------|----------------|----------|
| `USER`         | `user`               | `display-user` | 用户管理 CUD |
| `DEPT`         | `dept`               | `display-dept` | 部门管理 CUD |
| `ROLE`         | `role`               | `display-role` | 角色管理 CUD |
| `DICT_CATALOG` | `dict`               | `dict-catalog` | 字典管理 CUD |

```java
// 包：com.sts.common.cache

public enum CacheDomain {
    USER("user", "display-user"),
    DEPT("dept", "display-dept"),
    ROLE("role", "display-role"),
    DICT_CATALOG("dict", "dict-catalog");

    private final String cacheName;
    private final String caffeineCacheName;

    CacheDomain(String cacheName, String caffeineCacheName) {
        this.cacheName = cacheName;
        this.caffeineCacheName = caffeineCacheName;
    }

    public String cacheName() { return cacheName; }
    public String caffeineCacheName() { return caffeineCacheName; }

    public static CacheDomain fromCacheName(String cacheName) {
        for (CacheDomain d : values()) {
            if (d.cacheName.equals(cacheName)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown cacheName: " + cacheName);
    }
}

public enum ClearScope {
    ALL,
    ENTRY
}
```

| `ClearScope` | 含义               | Caffeine          |
|--------------|------------------|-------------------|
| `ALL`        | 清理该域整库 L1        | `invalidateAll()` |
| `ENTRY`      | 分条目；**必填** `key` | `invalidate(key)` |

`ENTRY` 的 `key`：用户/部门/角色为数字字符串；字典为 `dict_type:sys_user_status`。

---

## 5. Pub/Sub 失效（可执行契约）

| 项       | 值                                            |
|---------|----------------------------------------------|
| Channel | `sts:cache:evict`（`sts.cache.evict.channel`） |
| 消息体     | `CacheEvictMessage`                          |

```java
public record CacheEvictMessage(
    String cacheName,
    String key,
    ClearScope scope
) {}

public interface CacheEvictPublisher {
    void publish(CacheEvictMessage message);
}
```

```java
// 包：com.sts.admin.cache

@Component
@RequiredArgsConstructor
public class CacheEvictSubscriber {

    private final CaffeineCacheManager caffeineCacheManager;

    @PostConstruct
    void subscribe() { /* RedisMessageListenerContainer → sts:cache:evict */ }

    void applyLocalEvict(CacheEvictMessage msg) {
        CacheDomain domain = CacheDomain.fromCacheName(msg.cacheName());
        Cache<Object, Object> cache = caffeineCacheManager.get(domain.caffeineCacheName());
        if (msg.scope() == ClearScope.ALL) {
            cache.invalidateAll();
        } else {
            cache.invalidate(parseKey(msg.key(), domain));
        }
    }
}
```

**CUD 失效顺序**（用户/部门/角色/字典 Service 内）：

```text
AFTER_COMMIT → 本地 L1 invalidate → CacheEvictPublisher.publish
```

**禁止**：Redis DEL 业务缓存键（不存在 L2 数据层）。

---

## 6. HTTP API

### 6.1 查询注册表

```http
GET /api/v1/system/cache/registry
```

```

权限：`sys:cache:list`

```json
{
  "code": 0,
  "data": [
    { "domain": "USER", "cacheName": "user", "caffeineCacheName": "display-user", "supportsScopes": ["ALL", "ENTRY"] }
  ]
}
```

### 6.2 清理缓存

```http
POST /api/v1/system/cache/clear
```

```

权限：`sys:cache:clear`

```json
{
  "domain": "USER",
  "scope": "ENTRY",
  "key": "10023"
}
```

```java
// 包：com.sts.admin.controller.system

@RestController
@RequestMapping("/api/v1/system/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheAdminService cacheAdminService;

    @GetMapping("/registry")
    @SaCheckPermission("sys:cache:list")
    public Result<List<CacheRegistryItemVO>> registry() {
        return Result.ok(cacheAdminService.listRegistry());
    }

    @PostMapping("/clear")
    @SaCheckPermission("sys:cache:clear")
    @Log(title = "缓存管理", businessType = BusinessType.CLEAN)
    public Result<Void> clear(@RequestBody @Valid CacheClearRequest request) {
        cacheAdminService.clear(request);
        return Result.ok();
    }
}
```

```java
public interface CacheAdminService {
    List<CacheRegistryItemVO> listRegistry();
    void clear(CacheClearRequest request);
}

public record CacheClearRequest(CacheDomain domain, ClearScope scope, String key) {}
```

`clear` 步骤：校验 → 本地 L1 invalidate → `publish` → 可选 `@Log`。

---

## 7. 配置项

| 键                                              | 默认                | 说明                                    |
|------------------------------------------------|-------------------|---------------------------------------|
| `sts.cache.evict.channel`                      | `sts:cache:evict` | Pub/Sub                               |
| `sts.cache.caffeine.*.expire-after-write`      | `10m`             | **各域统一**（USER/DEPT/ROLE/DICT_CATALOG） |
| `sts.cache.caffeine.display-user.maximum-size` | `10000`           | 见 id-name-cache §2.5                  |

---

## 8. Audit 自检（system-module）

- [ ] `us_sys_menu` 存在「缓存管理」菜单（`C`）及 `sys:cache:clear` 按钮（`F`）
- [ ] 前端路由 `/system/cache` 与 `page-design.md` §2.2 一致
- [ ] `CacheAdminController` 路径 `/api/v1/system/cache/*`；权限 `sys:cache:*`
- [ ] `CacheDomain` / `ClearScope` 固定枚举
- [ ] CUD 与 `POST clear` 均 publish + 本地 L1 失效
- [ ] `CacheEvictSubscriber` 订阅 `sts:cache:evict`
