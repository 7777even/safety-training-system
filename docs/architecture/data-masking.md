# 敏感字段脱敏契约（@Sensitive + Jackson 序列化）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

## 适用范围

在 **Jackson 序列化** 阶段对敏感字段自动脱敏，覆盖两条链路：

| 链路     | 触发点                                                | 结果                                 |
|--------|----------------------------------------------------|------------------------------------|
| Web 响应 | Spring MVC 将 VO 序列化为 HTTP JSON                     | 前端收到脱敏值                            |
| 操作日志   | `OperLogAspect` 调用 `JsonSerializeUtil.serialize()` | `oper_param` / `json_result` 写入脱敏值 |

**数据库持久层不受任何影响**——脱敏仅发生在序列化时，明文原始对象进入 Service / Mapper 的路径与本机制无交叉。

---

## 核心组件

所有组件位于 `com.sts.admin.aop.sensitive`。

### 1. `@Sensitive` 注解

```java

@Sensitive(strategy = SensitiveStrategy.PHONE)
private String phone;
```

组合注解，内置 `@JacksonAnnotationsInside + @JsonSerialize(using = SensitiveJsonSerializer.class)`。
Jackson 在扫描 Bean 属性时自动发现并委托 `SensitiveJsonSerializer` 处理该字段，**无需额外 `ObjectMapper` 配置**。

### 2. `SensitiveStrategy` 枚举

| 枚举值         | 示例输入                 | 示例输出                 | 算法描述                |
|-------------|----------------------|----------------------|---------------------|
| `USERNAME`  | `张三丰`                | `张*丰`                | 首尾保留，中间全掩           |
| `PHONE`     | `13812345678`        | `138****5678`        | 前 3 后 4 保留，中间固定 4 星 |
| `ID_CARD`   | `110101199001011234` | `110101**********34` | 前 6 后 2 保留，中间 10 星  |
| `BANK_CARD` | `6222021234567890`   | `622202******7890`   | 前 6 后 4 保留，中间动态长度   |

所有算法在 `DesensitizerUtil` 中以纯 `String.substring` 实现，**无正则依赖**。

### 3. `SensitiveJsonSerializer`

实现 Jackson `ContextualSerializer`，在字段绑定阶段（非每次序列化）读取 `@Sensitive.strategy()`，
创建独立实例绑定该策略，后续序列化直接调用策略函数。`strategy == null` 时透传原值（兜底）。

---

## 密码字段处理

密码类字段（请求入参）使用 `@JsonProperty(access = WRITE_ONLY)`，而非 `@Sensitive`。

```java

@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String password;
```

效果：Jackson 反序列化可正常接收前端提交值，但任何序列化输出（日志、响应体）均**不含该字段**。

---

## 已标注字段清单（当前）

| 类                      | 字段         | 处理方式                        |
|------------------------|------------|-----------------------------|
| `UserVO`               | `phone`    | `@Sensitive(PHONE)`         |
| `UserCreateRequest`    | `phone`    | `@Sensitive(PHONE)`         |
| `UserCreateRequest`    | `password` | `@JsonProperty(WRITE_ONLY)` |
| `UserUpdateRequest`    | `phone`    | `@Sensitive(PHONE)`         |
| `ResetPasswordRequest` | `password` | `@JsonProperty(WRITE_ONLY)` |

---

## 如何扩展

**新增脱敏策略**：在 `DesensitizerUtil` 添加静态方法，在 `SensitiveStrategy` 增加枚举项并引用该方法。

**新增脱敏字段**：在目标 DTO / VO 字段上直接加 `@Sensitive(strategy = SensitiveStrategy.XXX)`，无需其他改动。

---

## 日志序列化安全截断

所有组件位于 `com.sts.admin.aop.util`。

`OperLogAspect` 不直接调用 `objectMapper.writeValueAsString()`，而是通过 `JsonSerializeUtil.serialize()` 进行流式截断序列化：

```
objectMapper.writeValue(LimitedWriter, obj)
        ↓
LimitedWriter 每次 write() 累加字符数
        ↓ 超过 (limit - 14) 字符
LimitExceededException（继承 IOException，跳过栈帧收集）
        ↓
JsonSerializeUtil catch → 已写内容 + "...(truncated)"
```

**三个类的职责**

| 类                        | 可见性             | 职责                                      |
|--------------------------|-----------------|-----------------------------------------|
| `LimitExceededException` | package-private | 控制流信号，非真实错误；覆盖 `fillInStackTrace()` 零开销 |
| `LimitedWriter`          | package-private | 包装 `StringWriter`，写入超配额时先写满再抛出          |
| `JsonSerializeUtil`      | public          | 唯一对外入口，`serialize(mapper, obj, limit)`  |

**截断长度保证**

`LimitedWriter` 的实际写入上限是 `limit - TRUNCATED_SUFFIX.length()`（即 `limit - 14`），追加后缀后总长度严格等于 `limit`
，不会超出 MySQL `TEXT` 列的 65535 字节上限。

**当前限制**

| 字段            | 常量                          | MySQL 列类型 |
|---------------|-----------------------------|-----------|
| `oper_param`  | `MAX_PARAM_LENGTH = 65535`  | `TEXT`    |
| `json_result` | `MAX_RESULT_LENGTH = 65535` | `TEXT`    |

截断结果是残缺 JSON（括号不闭合），末尾有 `...(truncated)` 标记，仅供人工审阅，不做程序解析。

---

## 与操作日志的联动

`OperLogAspect` 注入 Spring Boot 自动配置的 `ObjectMapper`，通过 `JsonSerializeUtil.serialize()` 触发脱敏与截断，两个能力在同一次序列化中完成，
**无需额外处理**。
正则兜底方案（`maskSensitiveFields`）已删除；脱敏责任下沉到字段注解层。

---

## 强制规则

1. **禁止**在 `OperLogAspect` 或 Controller 中用正则/字符串替换做脱敏；统一使用 `@Sensitive`。
2. **禁止**对持久层（Entity / Mapper）字段打 `@Sensitive`——脱敏只属于出参（VO）和日志（Request DTO）层。
3. 新增含手机号、身份证、银行卡字段的 VO 或 Request DTO **必须**标注对应 `@Sensitive`，否则审计不通过。

---

## Audit

- [x] `@Sensitive` 注解及相关组件位于 `aop/sensitive/` 包，不散落业务层
- [x] `DesensitizerUtil` 无正则，纯字符串截取
- [x] 密码字段使用 `WRITE_ONLY`，不出现在任何序列化输出
- [x] `OperLogAspect` 无正则脱敏残留
- [x] Entity 层无 `@Sensitive` 注解
- [x] `JsonSerializeUtil` 写入上限预留后缀长度，总输出严格 ≤ `limit`
- [x] `LimitExceededException` 覆盖 `fillInStackTrace()`，截断路径无栈帧开销
- [x] `OperLogAspect` 不直接调用 `writeValueAsString()`，统一走 `JsonSerializeUtil`
