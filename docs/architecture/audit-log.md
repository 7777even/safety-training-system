# 操作审计日志契约（@Log AOP）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

## 适用范围

对用户发起的**非幂等性（CUD）高危变动**强制留痕，写入 `us_sys_oper_log`（完整结构见
`backend/sts-service-admin-init/src/main/resources/schema/schema-system.sql`）。

## 实现规范

```java
@Log(title = "用户管理", businessType = BusinessType.DELETE)
@RequiresPermissions("sys:user:delete")
public Result<Void> deleteUser(Long userId) { ... }
```

- 通过 Spring AOP 切面拦截，**禁止**在 Controller 内手写审计逻辑。
- 必须记录：`oper_param`（请求 JSON）、`json_result`（响应结果）。
- Groovy 规则脚本等高危修改必须完整记录参数 JSON。
- 操作日志与登录日志**不可关闭**。
- 审计字段 `create_by` 等使用 **BIGINT 用户 ID**，禁止存用户名。

## 登录审计

登录/登出事件写入 `us_sys_login_log`，由 Auth 拦截层记录（见 `auth-design.md`）。

## 强制规则

1. 禁止阻塞核心业务接口：日志写入宜异步（Spring 事件或专用线程池）。
