# 🚀 统一 API 交互契约 — PageRequest / Result\<T\>

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **适用范围**：**`sts-service-admin`**、**`（已移除驱动模块）/*`** 的 Spring MVC HTTP API。  
> **HTTP Status 语义**见 **`global-exception.md`**（两模块同一套映射，禁止失败仍 200）。  
> **OpenAPI 文档怎么写**见 **`openapi-contract-writing-guide.md`**（字段注释、example、`x-permission`、与 Controller 对齐）。

---

## 一、 分页请求规范 (PageRequest)

所有分页查询接口必须使用 **通用泛型请求包装器**，将分页元数据与业务查询条件解耦。

### 1. 通用泛型基类 (sts-common)

```java
package com.sts.common.api;

import lombok.Data;
import jakarta.validation.Valid;

/**
 * 通用泛型分页请求包装器
 * @param <T> 具体的业务查询对象类型
 */
@Data
public class PageRequest<T> {

    /** 全局统一分页默认值（唯一真理源，禁止在 Service 层重复判空兜底） */
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private Integer pageNum;
    private Integer pageSize;

    @Valid
    private T query; // 业务查询条件对象

    /** 手写 getter 覆盖 Lombok：未传时返回默认值，调用方直接使用 int，无需判空 */
    public int getPageNum() { return pageNum != null ? pageNum : DEFAULT_PAGE_NUM; }
    public int getPageSize() { return pageSize != null ? pageSize : DEFAULT_PAGE_SIZE; }
}
```

> Service 层取分页参数**统一写法**：`new Page<>(request.getPageNum(), request.getPageSize())`，
> **禁止** `request.getPageNum() != null ? ... : 1` 式的重复判空（默认值已收口在基类 getter 与
> `PageRequestArgumentResolver`，两处均引用 `DEFAULT_PAGE_NUM / DEFAULT_PAGE_SIZE` 常量）。

### 2. 业务查询对象 (Query DTO)

业务查询对象只包含过滤字段，不包含分页字段。

```java

@Data
public class UserQuery {
   private String userCode;
   private String userName;
    private Integer status;
}
```

### 3. Controller 使用规范

Controller 方法统一接收 `PageRequest<T>`，GET 参数采用 `query.xxx` 点号前缀（例如：
`?pageNum=1&query.userCode=admin`），与前端 `request.ts#flattenParams` 的展平规则一一对应。

```java

@GetMapping
public PageResult<UserVO> listUsers(PageRequest<UserQuery> request) {
    return userService.pageUsers(request);
}
```

> ⚠️ **禁止**依赖 Spring MVC 默认数据绑定来填充 `PageRequest<T>`：由于运行期类型擦除，
> 默认 `ModelAttributeMethodProcessor` 会把字段 `query` 实例化为 `Object`，
> Service 层 `request.getQuery()` 强转时抛 `ClassCastException`。
> 必须由下述 §4 的自定义参数解析器完成绑定。
> 同理也**禁止**在 Controller 上用扁平 `@RequestParam` 手拼 Query DTO（与前端 `query.` 前缀不匹配，条件会被静默忽略）。

### 4. 可执行契约：PageRequestArgumentResolver（sts-common）

| 项     | 内容                                                                                                                                |
|-------|-----------------------------------------------------------------------------------------------------------------------------------|
| 全限定类名 | `com.sts.common.api.PageRequestArgumentResolver`                                                                         |
| 所属模块  | `sts-common`（与 `PageRequest` 同包）                                                                                                   |
| 实现接口  | `org.springframework.web.method.support.HandlerMethodArgumentResolver`                                                            |
| 注册方   | 各 HTTP 服务的 `WebMvcConfigurer#addArgumentResolvers`（admin：`com.sts.admin.config.WebMvcConfig`；driver 服务如有 HTTP API 须同样注册） |

**方法签名**

```java
public boolean supportsParameter(MethodParameter parameter);
// 仅当参数类型 == PageRequest.class 时生效

public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception;
```

**解析规则**

1. `pageNum` / `pageSize` 直接取请求参数，缺省值分别为 `1` / `20`（前端总会显式携带，缺省仅兜底）；
2. 通过 `ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve()` 取得方法签名上的真实泛型 `T`
   （方法参数泛型保留在字节码中，不受擦除影响）；
3. `BeanUtils.instantiateClass(T)` 实例化 Query DTO，用 `binderFactory.createBinder(webRequest, query, "query")` 创建绑定器，以
   `new ServletRequestParameterPropertyValues(request, "query", ".")` 收集并剥离 `query.` 前缀后执行 `binder.bind(...)`；
4. 绑定后执行 `binder.validate()`（兑现 `PageRequest.query` 上的 `@Valid` 语义）；存在绑定/校验错误时抛 `BindException`，由
   `HttpApiExceptionHandler#handleBind` 映射为 HTTP 400 + `code=1001`；
5. 泛型无法解析（裸 `PageRequest`）时 `query` 为 `null`；
6. 返回 `PageRequest.of(pageNum, pageSize, query)`。

---

## 二、 统一返回格式 (Result\<T\>)

### 1. 共享实现（sts-common）

| 类型                  | 位置                        | 说明                                      |
|---------------------|---------------------------|-----------------------------------------|
| `Result<T>`         | `sts-common` … `api`       | Java 侧统一响应体，admin / driver 共用           |
| `BusinessException` | `sts-common` … `exception` | 携带 `HttpStatus` + 业务 `code` + `message` |


### 2. JSON 结构定义

**成功**（`HTTP 200`）与 **失败**（`HTTP 4xx/5xx`）body 结构相同：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

前端 TypeScript 类型（真理源）：

```typescript
interface ApiResponse<T> {
    code: number
    message: string
    data: T
}

interface PageResult<T> {
    list: T[]
    total: number
}
```

### 3. 字段规范

| 字段        | 类型       | 必填 | 说明                                         |
|-----------|----------|----|--------------------------------------------|
| `code`    | Integer  | 是  | `0` = 业务成功；非零 = 业务细分码（**不替代** HTTP Status） |
| `message` | String   | 是  | 人类可读提示                                     |
| `data`    | T / null | 是  | 成功为载荷；失败可为 `null`                          |

---

## 三、 运行期行为与联动

### 1. 错误码分段

| 区间          | 含义           | 典型模块                                    |
|-------------|--------------|-----------------------------------------|
| `0`         | 成功           | admin / driver                          |
| `1000-1999` | 通用（参数、鉴权、限流） | admin / driver                          |
| `2000-2999` | 系统管理         | **admin**（`com.sts.admin.system.*`）     |
| `3000-3999` | 设备管理（北向）     | **admin**                               |
| `4000-4999` | 机构成员与公共组织支撑 | **admin**（`com.sts.org.*`）              |
| `5000-5999` | 课程业务         | **admin**（`com.sts.course.*`，同进程独立业务码段） |
| `6000-6999` | 素材业务         | **admin**（`com.sts.asset.*`）            |
| `9000-9999` | 未知/兜底        | admin / driver                          |

### 2. 与 global-exception 联动

| 时机              | HTTP                              | body                           |
|-----------------|-----------------------------------|--------------------------------|
| Controller 正常返回 | **200**                           | `Result.ok(data)` → `code = 0` |
| 校验/业务/系统异常      | **4xx/5xx**（见 global-exception 表） | `Result.fail(code, message)`   |

### 3. 与 ID→Name 联动（admin）

列表/详情 **`Result<PageResult<VO>>`** / **`Result<VO>`** 在 **JSON 写出前**，由 **`ResultEasyTransAdvice`**（
`id-name-cache.md` §2.4）对 `data` 内标注了 `@Translatable` 的 VO **整批**就地填充 `*Name` 伴生字段。

---

## 四、 强制规则

1. admin、driver 的 REST Controller 既可以直接返回业务 VO / 原始对象（由 `ResultEasyTransAdvice` 切面机制在 HTTP
   序列化之前自动包装为统一的 `Result<T>`），也可以返回 `Result<T>`。
2. **失败**必须由 `HttpApiExceptionHandler` 返回 **真实 HTTP Status**，禁止失败仍 200。
3. admin 分页 `data` 使用 `PageResult`（`list` + `total`）。
4. 业务对象封面受控 GET（`GET /api/{域}/{对象}/{id}/cover`）成功返回原始图片字节，**不包** `Result`；失败仍走本文件与
   `global-exception.md` 的真实 HTTP 状态 + `Result` 外壳。设计见 `cover-access-protocol.md`。
