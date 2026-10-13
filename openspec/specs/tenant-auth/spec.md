# tenant-auth Specification

## Purpose

TBD - created by archiving change 2026-08-10-bind-superadmin-login-tenant. Update Purpose after archive.

## Requirements

### Requirement: superAdmin 可选择登录租户

系统 MUST 允许账号编码精确为 `superAdmin` 的唯一平台账号使用自身平台凭证登录任一存在且启用的租户，
且 MUST NOT 要求该平台账号的 `organization_id` 与登录目标租户相同。

#### Scenario: superAdmin 登录其它企业

- **GIVEN** 唯一平台 `superAdmin` 账号归属租户 A
- **AND** 租户 B 存在且启用
- **WHEN** `superAdmin` 使用租户 B 的企业编码和正确的平台密码登录
- **THEN** 登录成功
- **AND** 会话登录租户为 B

#### Scenario: 普通用户仍校验所属租户

- **GIVEN** 普通账号归属租户 A
- **WHEN** 使用租户 B 的企业编码和该账号凭证登录
- **THEN** 系统拒绝登录
- **AND** 不泄露账号或租户是否存在

#### Scenario: 平台账号不唯一时失败关闭

- **GIVEN** 数据异常导致系统查到零个或多个 `superAdmin` 用户主体
- **WHEN** 尝试以 `superAdmin` 登录任意租户
- **THEN** 系统拒绝认证
- **AND** MUST NOT 任取一个主体继续登录

### Requirement: 企业开户只创建可登录管理员

`superAdmin` 创建企业时，系统 MUST 只创建企业主数据和一个归属新企业的可登录管理员账号，
MUST NOT 代建默认部门、其它账号或企业业务数据。开户管理员 MUST 允许 `dept_id` 为空。

#### Scenario: superAdmin 执行租户开户

- **GIVEN** `superAdmin` 已登录租户 A
- **WHEN** 创建租户 B
- **THEN** 系统创建租户 B 与一个归属 B 的管理员账号
- **AND** 该管理员可使用 B 的企业编码登录并管理 B
- **AND** 该管理员初始部门为空
- **AND** 系统不创建 B 的默认部门、岗位、标签、其它账号或业务数据
- **AND** 操作结束后请求上下文恢复为 A

#### Scenario: 未登记业务接口不能跨租户

- **GIVEN** `superAdmin` 已登录租户 A
- **WHEN** 调用普通业务接口并尝试访问租户 B 的记录
- **THEN** 系统按租户 A 的上下文执行
- **AND** MUST NOT 读取、修改或删除租户 B 的记录

### Requirement: 登录以企业编码确定租户

系统 MUST 在所有成功会话中记录登录目标租户与租户类型。普通用户账号归属 MUST 与登录目标租户一致；
`superAdmin` 可不受账号归属限制选择目标租户。后续接口 MUST 使用 TokenSession 中的登录租户，
MUST NOT 允许客户端参数覆盖。

#### Scenario: 登录租户贯穿整个会话

- **GIVEN** 用户以企业编码 A 登录成功
- **WHEN** 使用该 Token 调用任意普通业务接口
- **THEN** 系统只使用会话中的租户 A 执行数据隔离
- **AND** 不要求客户端再次提交企业编码

#### Scenario: 不泄露租户存在性

- **WHEN** 使用不存在的企业编码登录，或使用正确企业编码与错误密码登录
- **THEN** 系统返回相同的失败话术

### Requirement: 角色只表达功能权限

账号编码精确为 `superAdmin` 的用户继续拥有 `*` 功能权限，但该身份 MUST NOT 自动扩大普通业务数据范围；
其普通业务数据范围等于本次登录租户。

#### Scenario: 超级权限不扩大普通业务数据范围

- **GIVEN** `superAdmin` 以租户 A 登录并拥有 `*` 功能权限
- **WHEN** 查询素材、文件、课程或人员等普通业务数据
- **THEN** 权限校验通过
- **AND** 查询结果仍只包含租户 A 的数据

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

