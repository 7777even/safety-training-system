# 🚀 本地缓存与高性能「存 ID 显 Name」转换规范 (基于 EasyTrans 框架)

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **底层框架**：全面基于 **EasyTrans** (基于 APT 的超高性能关联 ID 自动批量就地翻译框架) 代替传统反射方案，实现 **100% 零反射、编译期硬编码就地回填** 的极致性能。  
> **缓存**：**仅 L1 Caffeine**；**无 L2 Redis 数据缓存**；失效与清理见 [缓存管理设计](./system-admin/cache-admin.md)
> （Pub/Sub + 运维清理 API）。
> **配合**：与 `api-standard.md`（统一 API 契约）、[字典目录设计](./system-admin/dict-catalog.md) 配合使用。

---

## 📂 一、 缓存视图与流向设计 (基于 EasyTrans)

针对用户名称、组织树节点、角色等**实体引用**，拒绝 SQL `LEFT JOIN`，采用 **Caffeine (L1) 本地缓存 + EasyTrans APT
静态就地回填 + 批量 DB 回源** 模式（字典见 [字典目录设计](./system-admin/dict-catalog.md)）：

```text
[ Controller 返回 Result<T> ]
             │
             ▼
[ ResultEasyTransAdvice ]  ← 统一返回拦截（见 §二.1、§2.4）
             │   就地翻译 (In-place Translation) 一次性整批执行
             │
├──► 1. ID 收集阶段 (Bridge.extractAllIds) ----► 编译期硬编码递归、极速收集嵌套关联 ID（Set<Long>）
├──► 2. 批量加载阶段 (Feeder.batchLoad) -------► L1 getAllPresent -> 未命中 keys 批量查 DB -> putAll 进 L1
└──► 3. 属性就地回填阶段 (Bridge.writeBack) ----► 纯硬编码 setter 极速写入 Name 字段 -> Jackson 序列化

[ CUD / 运维清缓存 ]
             │
├──► 本地 L1 invalidate（ALL 或 ENTRY）
└──► Redis Pub/Sub `sts:cache:evict` → 其它节点 L1 invalidate（见 `system-admin/cache-admin.md`）
```

1. **L1 本地缓存 (Caffeine)**：全系统统一采用 **Caffeine 3.2.4**（见 `stack.md` 锁定的高性能本地缓存版本），配置
   `maximumSize` + `expireAfterWrite=10m`。由各自 `TranslationFeeder` 实现类的底层 Cache 仓储（如
   `DisplayUserCacheRepository`）持有和控制，不进行全局 Redis 数据缓存。
2. **无 L2 数据缓存**：**100% 禁用**在 Redis 中存储用户名称、组织名等业务字符串。Redis **仅**作为集群节点间 L1 缓存失效的
   Pub/Sub 广播通道。
3. **APT 零反射优势**：传统的反射 AOP 切面会产生大量的 `setAccessible`、`getDeclaredFields` 等 CPU 损耗。EasyTrans 通过 Java 编译期注解处理器 (APT)，为每个标注 `@Translatable` 的 VO 类自动生成编译期 Bridge。Bridge 内部采用直接的 `getId()` 与 `setDeptName(...)` 纯硬编码调用，执行效率接近物理极限。

---

## 🛠️ 二、 运行期核心原理与实现规约 (Implementation Patterns)

### 1. 统一返回联动 + 批量归总红线

> 与 **`api-standard.md`** 绑定：凡 Controller 返回 **`Result<T>`** / **`Result<PageResult<T>>`**，由 *
*`ResultEasyTransAdvice`**（`ResponseBodyAdvice`，§2.4）在 **HTTP 序列化之前** 统一拦截。利用 EasyTrans 自动就地转义，对 *
*同一响应批次** 一次性、批量完成 ID→Name。

**禁止**：
- 在 **for/foreach 循环体内** 单条查 MySQL/Redis（杜绝 N+1 I/O 灾难）；
- 使用 **Jackson 自定义序列化器** 在 Jackson 运行期通过反射/AOP 动态织入虚字段；
- 使用 **@Aspect 切 Controller 方法** 替代 `ResponseBodyAdvice`（统一返回必须走 ResponseBodyAdvice 机制）；
- 在 Service / Mapper 层对列表进行 **逐行** 翻译。

**允许的唯一执行链路**：
```text
Controller  return Result.ok(pageResult);
    → ResultEasyTransAdvice.beforeBodyWrite
    → unwrap data（PageResult.list / List / 单条）
    → 判定 VO 是否标注 @Translatable
    → 驱动 EasyTrans 引擎一键就地翻译 (In-place Translation)
         → 第一阶段：Bridge.extractAllIds (零反射、纯硬编码递归收集嵌套关联 ID)
         → 分发给各 Feeder.batchLoad (聚合 ID 去重分类，分发批量 SQL IN 查询)
              → L1 Caffeine 命中则直接返回
              → 未命中批量查 MySQL 回源，并异步 put 到本地 L1 中
         → 第二阶段：Bridge.writeBack (纯硬编码 setter 极速就地回填展示 Name)
    → HttpMessageConverter 序列化 JSON 输出给前端
```

| 响应 `data` 形态 | Advice 拆批方式 |
|------------------|-----------------|
| `PageResult<VO>` | `page.getList()` |
| `List<VO>` | 原 list |
| 单个 `VO` | `List.of(data)` |
| 非 VO / 未 `@Translatable` | `EasyTrans` 判定未标注，**O(1) 毫秒级跳过**，原样序列化 |

### 2. 事务后置失效（Pub/Sub，无 L2）

当管理后台触发用户、部门或角色等关联实体的 CUD（增删改）操作时，必须 100% 保证本地 L1 Caffeine 的强一致性：

1. **`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`** 触发；
2. **当前操作节点**本地 L1 缓存立即执行对应 Caffeine 实例的 **`invalidate(key)`**（或者 `invalidateAll`）；
3. **`CacheEvictPublisher`** 发布 `CacheEvictMessage` 到 Redis 广播通道（见 [缓存管理设计](./system-admin/cache-admin.md)
   §3）；
4. **集群其它节点**的 `CacheEvictSubscriber` 监听到通道消息后，同步在本地执行相应的 Caffeine `invalidate`，抹平集群单机内存快照差。

---

## 📦 三、 返回对象与伴生字段设计 (VO / 对外契约)

与 `api-standard.md` 联动：HTTP 响应为 `Result<T>` 或 `Result<PageResult<T>>`，**`T` 为业务 VO**；ID 与展示名**成对出现**
，禁止只返回 Name 丢失 ID。

### 1. 双字段原则 (存 ID，显 Name)

| 原则 | 说明 |
| :--- | :--- |
| **主键/外键保留** | 数据库外键映射为 Java 属性（如 `deptId`、`createBy`），**必须原样返回**，用于编辑回显、权限隔离、二次提交。 |
| **伴生展示字段** | 仅用于页面表格、详情**展示**（如 `deptName`、`createByName`），由 EasyTrans 翻译回填，**不入库、不参与写请求体校验**。 |
| **禁止替换** | 不得使用 `deptName` 顶替 `deptId` 作为唯一返回属性；不得 SQL `LEFT JOIN` 把 name 查进主实体替代 id。 |
| **写接口** | POST/PUT 对应的请求 DTO **只收 ID**（如 `deptId`），**绝对不收** `deptName`。 |

### 2. 伴生字段命名约定

| 源字段类型 | 源字段示例 | 伴生字段名 | 填充方 |
| :--- | :--- | :--- | :--- |
| 用户 ID | `createBy` / `updateBy` | `createByName` / `updateByName` | EasyTrans 后端回填 |
| 部门/组织 ID | `deptId` | `deptName` | EasyTrans 后端回填 |
| 角色/菜单等 ID | `roleId` | `roleName` | EasyTrans 后端回填 |
| 字典 code | `status` | （无 `statusText` 出参） | **前端 dictStore 本地查表** |

---

## 🛠️ 四、 可执行契约 (基于 EasyTrans 2.0 规范)

> Implement 阶段必须与本节定义的包路径、类名、方法签名、注解使用完全一致。包基准：`com.sts`。

### 1. 依赖与 Maven 配置

在 `stack.md` 及各组件构建脚本中，声明 EasyTrans 2.0 核心依赖。**由于 EasyTrans 2.0 纯靠静态 APT 生成，不需要额外引入 MapStruct 依赖**：

```groovy
dependencies {
    // EasyTrans Spring Boot 3/4 自动装配 Starter (包含 annotation 定义)
    implementation 'io.github.davidricardo1026:easytrans-spring-boot-starter:2.0.0'
    
    // APT 编译器处理器 (在编译期自动生成 Bridge 桥接类及注册表)
    annotationProcessor 'io.github.davidricardo1026:easytrans-processor:2.0.0'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
}
```

### 2. 注解定义及 VO 使用

#### 2.1 引入框架注解
- **`io.github.davidricardo1026.easytrans.annotation.Translatable`**：修饰在 VO 类上，表示该类可以被翻译。
- **`io.github.davidricardo1026.easytrans.annotation.TranslateField`**：修饰在展示伴生字段上。
  - `source`：源属性名。
  - `type`：翻译服务唯一的业务标识（Feeder 注册类型）。

#### 2.2 实体 VO 契约示例

```java
package com.sts.admin.api.vo;

import io.github.davidricardo1026.easytrans.annotation.Translatable;
import io.github.davidricardo1026.easytrans.annotation.TranslateField;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Translatable // 开启 APT 编译期 Bridge 自动生成
public class UserVO {
    private Long userId;
    private String userCode;
    private String userName;
    private Integer status; // 字典 code 仅由前端 dictStore 解析，不含 statusText，不参与后端转义
    
    private Long deptId;
    @TranslateField(source = "deptId", type = "DEPT_SERVICE") // 源自 deptId，转义服务为 DEPT_SERVICE
    private String deptName;

    private Long createBy;
    @TranslateField(source = "createBy", type = "USER_SERVICE") // 源自 createBy，转义服务为 USER_SERVICE
    private String createByName;

    private LocalDateTime createTime;
}
```

### 3. TranslationFeeder SPI 接口与具体实现类

所有批量反查实现类必须实现 EasyTrans 提供的 **`TranslationFeeder`** 接口：

```java
package io.github.davidricardo1026.easytrans.feeder;

import java.util.Map;
import java.util.Set;

public interface TranslationFeeder {
    /**
     * 定义该 Feeder 唯一的业务转义服务类型。
     * 例如："USER_SERVICE", "DEPT_SERVICE"
     */
    String getType();

    /**
     * 批量反查。禁止在此方法内部逐条循环查 DB 导致 N+1。
     * 必须利用 L1 Caffeine 进行内存反查，未命中则批量 In 回源。
     *
     * @param ids 框架硬编码递归抽取并去重后的 ID 集合
     * @return 关联映射 Map<Id, String> (未命中的 ID 不应出现在 Map 中，由框架赋 null)
     */
    Map<Object, String> batchLoad(Set<Object> ids);
}
```

#### 3.1 用户服务加载器：`UserTranslationFeeder` (sts-service-admin)

```java
package com.sts.admin.display.feeder;

import io.github.davidricardo1026.easytrans.feeder.TranslationFeeder;
import com.sts.admin.display.cache.DisplayUserCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserTranslationFeeder implements TranslationFeeder {

    private static final String L1_CACHE_NAME = "display-user";
    private final DisplayUserCacheRepository userCacheRepository;

    @Override
    public String getType() {
        return "USER_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // 将 Object 类型安全的规整为 Long 集合进行底层 Caffeine + DB 批量反查
        Set<Long> userIds = ids.stream()
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toSet());
                
        Map<Long, String> result = userCacheRepository.getDisplayNames(userIds, L1_CACHE_NAME);
        
        // 转换回 Map<Object, String> 满足接口签名
        return result.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
```

**底层缓存仓储接口契约**（见 §2.5，纯 L1 + 批量 MySQL 回源，**无 L2 缓存**）：
```java
package com.sts.admin.display.cache;

import java.util.Map;
import java.util.Set;

public interface DisplayUserCacheRepository {
    /**
     * 底层实现逻辑：
     * 1. 优先调用 Caffeine(display-user).getAllPresent(userIds)；
     * 2. 差集部分，拼装批量 SQL：SELECT user_id, user_code, user_name FROM us_sys_user WHERE user_id IN (...)；
     * 3. 批量查出后，展示名优先选用 user_name，无则选用 username，填充入 Map，并 `putAll` 回源写入本地 Caffeine 实例；
     * 4. 汇总 L1 命中 + 回源查出的全部映射返回。
     */
    Map<Long, String> getDisplayNames(Set<Long> userIds, String l1CacheName);
}
```

#### 3.2 组织部门服务加载器：`DeptTranslationFeeder` (sts-service-admin)

```java
package com.sts.admin.display.feeder;

import io.github.davidricardo1026.easytrans.feeder.TranslationFeeder;
import com.sts.admin.display.cache.DisplayDeptCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeptTranslationFeeder implements TranslationFeeder {

    private static final String L1_CACHE_NAME = "display-dept";
    private final DisplayDeptCacheRepository deptCacheRepository;

    @Override
    public String getType() {
        return "DEPT_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> deptIds = ids.stream()
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toSet());
                
        Map<Long, String> result = deptCacheRepository.getDisplayNames(deptIds, L1_CACHE_NAME);
        return result.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
```

#### 3.3 角色服务加载器：`RoleTranslationFeeder` (sts-service-admin)

```java
package com.sts.admin.display.feeder;

import io.github.davidricardo1026.easytrans.feeder.TranslationFeeder;
import com.sts.admin.display.cache.DisplayRoleCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleTranslationFeeder implements TranslationFeeder {

    private static final String L1_CACHE_NAME = "display-role";
    private final DisplayRoleCacheRepository roleCacheRepository;

    @Override
    public String getType() {
        return "ROLE_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = ids.stream()
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toSet());
                
        Map<Long, String> result = roleCacheRepository.getDisplayNames(roleIds, L1_CACHE_NAME);
        return result.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
```

---

### 4. 统一拦截联动：`ResultEasyTransAdvice` (ResponseBodyAdvice 机制)

> **与 `api-standard.md` 联动**：所有 Controller 返回的 `Result<?>` 均经由此切面拦截，统一调用 EasyTrans 的静态就地翻译引擎，执行
> ID 收集、批量 Feeder 加载、以及最终 Bridge 属性就地回填。
> **拒绝反射，O(1) 过滤**：只有当 `Result.getData()` 内的对象（或其包含的集合元素）上标注了 `@Translatable` 注解时，才由 EasyTrans 引擎进行硬编码就地填充；非翻译类（如基础类型、Map、未标注实体）通过编译期静态注册表判定，在 `O(1)` 时间复杂度内快速放行，0 运行期负担。

```java
package com.sts.admin.display.advice;

import com.sts.common.api.Result;
import com.sts.common.api.PageResult;
import io.github.easytrans.core.spi.TranslationExecutor; // EasyTrans 2.x 实际入口（非 EasyTransEngine）
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
@Order(value = Integer.MIN_VALUE + 10) // 确保在统一返回序列化前尽早拦截处理
public class ResultEasyTransAdvice implements ResponseBodyAdvice<Object> {

    private final TranslationExecutor translationExecutor; // 注入 EasyTrans 2.x 翻译执行器

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Result<?> result)) {
            return body;
        }
        Object data = result.getData();
        if (data == null) {
            return body; // 快速过路
        }
        
        // 拆包 data，联动 EasyTrans 转义引擎就地转义
        unwrapAndTranslate(data);
        return body;
    }

    private void unwrapAndTranslate(Object data) {
        if (data instanceof PageResult<?> page) {
            translateIfTranslatable(page.list());
            return;
        }
        if (data instanceof List<?> list) {
            translateIfTranslatable(list);
            return;
        }
        translateIfTranslatable(List.of(data));
    }

    private void translateIfTranslatable(List<?> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Object firstItem = list.get(0);
        if (firstItem == null || isSkippedType(firstItem.getClass())) {
            return;
        }
        
        // 调用 EasyTrans 就地转义引擎。EasyTrans 会根据编译期生成的静态注册表判断：
        // 若该类未标注 @Translatable，将执行 O(1) 静态拦截直接 return；
        // 若已标注，将极速调用编译生成的 Bridge 完成 (1)extractAllIds -> (2)batchLoad -> (3)writeBack 流程。
        translationExecutor.translate(list);
    }

    private static boolean isSkippedType(Class<?> type) {
        return type.isPrimitive()
            || type.getPackageName().startsWith("java.")
            || Map.class.isAssignableFrom(type)
            || Collection.class.isAssignableFrom(type);
    }
}
```

---

## 📂 五、 编译期代码生成行为与位置说明

EasyTrans 的 APT 注解处理器 `easytrans-processor` 在项目代码编译（`compileJava`）时自动运作：

1. **APT 文件扫描**：寻找所有标注了 `@Translatable` 的实体 VO（如 `UserVO`）。
2. **Bridge 桥接类生成**：为 `UserVO` 自动生成对应的转义桥接代码（包含静态方法 `extractAllIds` 与 `writeBack`），默认输出包路径为：`com.sts.admin.api.vo.generated.UserVOBridge.class`。
3. **Registry 注册表自动生成**：生成全局静态转义映射中心，用于请求期 Advice 依据 VO Class 类型快速、O(1) 复杂度路由至对应 Bridge 实例，位置为：`com.sts.admin.api.vo.generated.registry.GeneratedTranslationRegistry.class`。
4. **100% 静态，彻底挥别动态运行时反射**：所有的获取属性与回填均转化为常规的 Java 编译后代码（不使用 JDK 反射 Method/Field 读写，不存在 `setAccessible(true)`），运行性能完美拉满。

---

## 📦 六、 对外 JSON 示例

北向 HTTP 响应契约严格吻合统一返回规范，`data` 中**原样含有源外键 ID**，且**虚属性（伴生字段）就地翻译**。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "userId": 10023,
        "userCode": "zhangsan",
        "userName": "张三",
        "status": 1,
        "deptId": 2001,
        "deptName": "茂名石化",
        "createBy": 1,
        "createByName": "系统管理员",
        "createTime": "2026-06-01T10:00:00"
      }
    ],
    "total": 1
  }
}
```

* **前端交互**：表格及只读列绑定 `deptName`、`createByName`；字典列由于未参与后端 EasyTrans 转义，前端继续直接基于 `status` 调用 `dictStore.label('sys_user_status', row.status)` 进行本地翻译；编辑表单回显及下拉修改**仅提交** `deptId`，不提交伴生字段。

---

## ⚙️ 七、 实现落点分配 (admin / driver)

| 模块 | 包路径/技术落点 | 职责内容 |
| :--- | :--- | :--- |
| **sts-common** | Maven 核心声明及 EasyTrans Starter 引入 | 配置全局统一的 APT 注解处理器和框架注解引用。 |
| **sts-service-admin** | `com.sts.admin.display.feeder` | 编写各自实体的 `TranslationFeeder` 实现类（如 `UserTranslationFeeder`）。 |
| **sts-service-admin** | `com.sts.admin.display.cache` | 编写各领域纯 L1 Caffeine 的二级缓存仓储。 |
| **sts-service-admin** | `com.sts.admin.display.advice` | 部署 `ResultEasyTransAdvice` 控制层 ResponseBody 联动拦截器。 |

---

## 🎯 八、 Audit 阶段自检清单

- [ ] **框架完全适配**：所有的 VO 类（如 `UserVO`）均使用 EasyTrans 标准注解 `@Translatable` 与 `@TranslateField` 驱动；
- [ ] **移除反射残留**：项目中彻底移除原先手写的 `DisplayMetadataRegistry`、`DisplayMetadataBootstrap`、旧 `ResultDisplayEnrichAdvice` 反射样板代码及 AOP 切面；
- [ ] **加载器完备**：各业务实体外键对应的 `TranslationFeeder`（用户、部门、角色等 Feeder）均已在 `sts-service-admin` 完成 Component 注册与声明，方法内部禁止循环查库；
- [ ] **Caffeine 强缓存**：底层仓储仅依赖 L1 Caffeine 缓存，各域 `expireAfterWrite` 统一规定为 **`10m`**，禁用 Redis L2 数据缓存；
- [ ] **拦截准确过滤**：`ResultEasyTransAdvice` 只对拦截到的 `Result` 包裹有效 `data` 运行，利用静态注册表进行 `O(1)` 非翻译类直接拦截过滤，不滥用转义引擎。
