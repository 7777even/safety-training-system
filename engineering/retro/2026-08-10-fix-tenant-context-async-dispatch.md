# Servlet ASYNC 分派租户上下文修复 Retro（2026-08-10）

## 做得好的地方

- 根据线上表名和线程错误追到 Servlet 分派边界，没有把问题误判为 TokenSession 未写入或重新放开 `superAdmin` 旁路。
- 测试先稳定复现 ASYNC 分派上下文为空，再以标准 `OncePerRequestFilter` 扩展点修复。
- 同时固定 Filter 的 DispatcherType、执行顺序和清理行为，避免只让直接单测通过而容器运行期仍跳过过滤器。

## 问题与原因

- 之前只处理了 Sa-Token 在 StreamingResponseBody ASYNC 二次分派中的重复鉴权，没有同步检查租户过滤器的分派策略。
- TokenSession 是跨请求状态，`TenantContext` 是线程状态；首次 REQUEST 写入正确不代表切换后的容器线程自动具备租户。

## 改进

- 新增 Servlet 异步响应时，固定联合检查认证上下文、租户上下文、审计上下文和 ThreadLocal 清理。
- Filter 相关测试同时覆盖业务行为和真实注册属性，尤其是顺序、DispatcherType 与 asyncSupported。
- 后续遇到“会话有值但 Mapper 报上下文缺失”，优先记录 URI、DispatcherType 和线程名，区分首次请求、ASYNC 与后台任务。
