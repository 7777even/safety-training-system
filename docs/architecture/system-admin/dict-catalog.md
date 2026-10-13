# 字典全量目录 API（与 VO 伴生反查分离）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **分类**：架构功能点。与 `id-name-cache.md`（**仅实体 ID → Name**）并列，**字典 code → 文案不在 HTTP 列表接口反查**。

## 1. 设计目标

| 目标   | 说明                                                                                            |
|------|-----------------------------------------------------------------------------------------------|
| 一次拉取 | 前端在应用启动（或登录后）请求**整包字典目录**，后续页面本地查表                                                            |
| 接口瘦身 | 业务 `UserVO` 等**只返回** `status` / `deviceStatus` 等 code，**不返回** `statusText`                    |
| 职责分离 | 用户/部门/角色 → `@Translatable` + `TranslationFeeder` (见 `id-name-cache.md`)；字典 → 本 API + 前端 Store |
| 缓存复用 | 字典目录 **仅 L1** + Pub/Sub 失效（[cache-admin.md](./cache-admin.md)）                                |

## 2. 可执行契约（admin）

### 2.1 Controller

```java
// 包：com.sts.admin.controller.system

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictCatalogController {

    private final DictCatalogService dictCatalogService;

    @GetMapping("/catalog")
    public Result<DictCatalogVO> getCatalog(
            @RequestParam(required = false) String sinceVersion) {
        return Result.ok(dictCatalogService.getCatalog(sinceVersion));
    }
}
```

### 2.2 Service

```java
// 包：com.sts.admin.service.system

public interface DictCatalogService {

    /**
     * @param sinceVersion 可选；与当前 version 相同则返回 data=null（或 HTTP 304，Implement 二选一并在 OpenAPI 注明）
     */
    DictCatalogVO getCatalog(String sinceVersion);
}
```

### 2.3 DTO（sts-common 或 admin.api）

```java
// 包：com.sts.common.api.dict

public record DictCatalogVO(
        String version,
        Map<String, List<DictItemVO>> types
) {
}

public record DictItemVO(
        String value,
        String label,
        Integer sort,
        String cssClass
) {
}
```

### 2.4 缓存与 version（仅 L1）

| 项                        | 值                                                                                     |
|--------------------------|---------------------------------------------------------------------------------------|
| L1                       | Caffeine `dict-catalog`（`CacheDomain.DICT_CATALOG`），`expireAfterWrite=10m`            |
| **无 Redis 存 catalog 正文** | 未命中 L1 → 批量查 DB 组装 → `put` L1                                                         |
| `version`                | `max(us_sys_dict_type.update_time, us_sys_dict_data.update_time)` → `yyyyMMddHHmmss`  |
| 失效                       | CUD：`CacheEvictMessage(cacheName=dict, scope=ENTRY, key=dict_type:xxx)` 或 `scope=ALL` |

```java
// 包：com.sts.admin.service.system.impl（示意）

@Override
public DictCatalogVO getCatalog(String sinceVersion) {
    DictCatalogVO cached = dictCatalogCacheRepository.getFullCatalog();
    if (sinceVersion != null && sinceVersion.equals(cached.version())) {
        return null; // 或抛 304，与 Controller 约定一致
    }
    return cached;
}
```

---

## 3. 对外 API（admin）

### 3.1 全量目录

```http
GET /api/system/dict/catalog
```

**鉴权**：已登录会话（与 `auth-design.md` 一致）；可按租户/应用过滤（若多租户扩展）。

**响应**（包在 `Result<DictCatalogVO>` 内，HTTP 200）：

```json
{
  "version": "20260601120000",
  "types": {
    "sys_user_status": [
      {
        "value": "0",
        "label": "停用",
        "sort": 1,
        "cssClass": "danger"
      },
      {
        "value": "1",
        "label": "正常",
        "sort": 2,
        "cssClass": "success"
      }
    ],
      {
        "value": "online",
        "label": "在线",
        "sort": 1
      }
    ]
  }
}
```

| 字段                  | 含义                                                     |
|---------------------|--------------------------------------------------------|
| `version`           | 全表逻辑版本（字典 CUD 后递增或时间戳），前端用于判断是否刷新本地缓存                  |
| `types`             | key = `dict_type`（与库表 `us_sys_dict_type.dict_type` 一致） |
| `value`             | 存库/接口用的 code（字符串统一，数字字典也序列化为字符串）                       |
| `label`             | 展示文案（原 `dict_label`）                                   |
| `sort` / `cssClass` | 可选，供 `el-tag` / `el-select` 使用                         |

**禁止**：按 `dict_type` 拆成 N 个接口让列表页逐个拉（除非运维工具单类型编辑页）。

### 3.2 增量（可选，二期）

```http
GET /api/system/dict/catalog?sinceVersion=20260601120000
```

- 未变更 → `304` 或 `data: null` + 原 `version`；
- 已变更 → 返回完整 `types`（实现简单时始终全量亦可）。

## 4. 后端实现要点

| 项        | 约定                                                                                                           |
|----------|--------------------------------------------------------------------------------------------------------------|
| 数据源      | `us_sys_dict_type` + `us_sys_dict_data`，见 `./`                                                               |
| 组装       | Service 一次查询启用中的 type + data，内存 groupBy `dict_type`                                                          |
| 缓存       | 仅 L1 `dict-catalog`；CUD / 运维 `POST /api/v1/system/cache/clear` + Pub/Sub（[cache-admin.md](./cache-admin.md)） |
| VO 出参    | 业务列表 **不含** `*Text`；OpenAPI 只声明 code 字段                                                                      |
| 与 Enrich | **不**使用 `@TranslateField` 字典关联、**不**提供 `DictTranslationFeeder`                                               |

## 5. 与缓存失效联动

字典管理 CUD 提交成功后：

1. DB 事务 commit；
2. 本节点 L1 `dict-catalog` invalidate（ENTRY 或 ALL）；
3. `CacheEvictPublisher.publish`；
4. 递增 `version`；前端 `loadCatalog()`（见 `dict-catalog.md`）。

## 6. 强制规则

1. 新建业务接口**不得**为字典字段增加 `statusText` 并由 AOP 填充。
2. 导出/打印等若必须由后端出文案，走**独立**导出服务或显式调用字典 Service，**不**走 `@Translatable` 关联填充。
3. `driver` 若需字典展示：同样消费 catalog API 或 driver 专用只读 catalog 端点，原则一致。

## 7. Audit 自检

- [ ] `DictCatalogController.getCatalog` 路径与 §2.1 一致
- [ ] `DictCatalogVO` / `DictItemVO` 字段与 §2.3 一致
- [ ] 业务 list/detail VO 无 `*Text` 字典伴生字段
- [ ] 字典 catalog 仅 L1；无 Redis 存 catalog JSON
- [ ] 字典 CUD 后 Pub/Sub + [cache-admin.md](./cache-admin.md) 清理契约
- [ ] `id-name-cache.md` 范围不含 `@DictRef`
