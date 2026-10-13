# Servlet ASYNC 分派租户上下文修复 QA（2026-08-10）

## 范围

OpenSpec Change：`2026-08-10-fix-tenant-context-async-dispatch` Task 1，lims 纯后端范围；无 API、数据库或前端变化。

## TDD 证据

- Red：为普通用户、`superAdmin` 和异常清理新增 ASYNC 分派测试；现有实现出现 1 个失败、2 个错误，第一现场均为
  `TenantContext.requireTenantId()` 报“租户上下文缺失”。
- Green：`TenantContextFilter` 处理 ASYNC 分派；显式注册 REQUEST/ASYNC DispatcherType，并保持位于 Sa-Token 上下文过滤器之后。
- 回归：补充缺租户 ASYNC 会话返回 401、正常/异常结束清理 ThreadLocal、`superAdmin` 不进入旁路、Sa-Token 不重复鉴权。

## 已执行验证

- 定向测试：`TenantContextFilterTest` 7/7、`SaTokenConfigTest` 8/8 通过。
- Admin 零 DB：`mvn -pl sts-service-admin -am test`，561/561 通过。
- OpenSpec：`openspec validate 2026-08-10-fix-tenant-context-async-dispatch --strict` 通过。
- 静态检查：`git diff --check` 通过。
- 未执行 MySQL IT：本次未修改 Entity、Mapper、SQL、schema、事务或持久化语义，过滤器分派行为由零 DB 测试直接覆盖。

## Review

- 规格符合性：通过。REQUEST/ASYNC 均从 TokenSession 绑定租户；ERROR、API、数据库和前端范围未变化。
- 代码质量：通过。未使用 InheritableThreadLocal 或请求级旁路；每次分派仍在 `finally` 中清理上下文；Filter 注册顺序和
  DispatcherType 有测试固定。
