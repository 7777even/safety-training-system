## MODIFIED Requirements

### Requirement: 租户隔离由数据访问层强制执行

系统 MUST 对包括 `superAdmin` 在内的所有普通 HTTP 业务请求及其 Servlet ASYNC 二次分派注入登录租户条件，
并在 INSERT 时填充登录租户的 `organization_id`。每次分派结束后 MUST 清理线程租户上下文；只有设计中登记的
跨租户能力可在最小范围旁路。

#### Scenario: superAdmin 普通写入自动归属登录租户

- **GIVEN** `superAdmin` 以租户 A 登录
- **WHEN** 新增素材或完成文件上传
- **THEN** 新记录的 `organization_id` 为 A
- **AND** 不依赖请求体显式传递 `organization_id`

#### Scenario: 旧会话缺少租户信息

- **GIVEN** 改造前的 `superAdmin` TokenSession 没有 `organizationId`
- **WHEN** 使用该 Token 访问受隔离业务接口
- **THEN** 系统失败关闭并要求重新登录
- **AND** MUST NOT 回退为全局旁路

#### Scenario: 异步二次分派恢复登录租户

- **GIVEN** 用户已选择租户 A 登录，且第一次 REQUEST 分派已通过鉴权
- **WHEN** 流式响应触发 Servlet ASYNC 二次分派并访问租户业务表
- **THEN** 系统从同一 TokenSession 重新绑定租户 A
- **AND** SQL 继续注入租户 A 的隔离条件
- **AND** 分派结束后清理当前线程的租户上下文

#### Scenario: superAdmin 异步分派不扩大数据范围

- **GIVEN** `superAdmin` 以租户 A 登录并拥有 `*` 功能权限
- **WHEN** 素材转码任务查询或媒体流式响应进入 ASYNC 二次分派
- **THEN** 当前租户仍为 A
- **AND** MUST NOT 进入全局租户旁路

#### Scenario: 异步分派缺少有效租户身份

- **GIVEN** ASYNC 分派没有有效登录态或 TokenSession 缺少 `organizationId`
- **WHEN** 请求尝试进入受租户隔离的业务处理
- **THEN** 系统失败关闭
- **AND** MUST NOT 执行未隔离的租户表 SQL
