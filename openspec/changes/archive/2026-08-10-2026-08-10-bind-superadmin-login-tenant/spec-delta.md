# Spec Delta：superAdmin 登录租户绑定

## ADDED Requirements

### Requirement：superAdmin 可选择登录租户

系统 MUST 允许账号编码精确为 `superAdmin` 的唯一平台账号使用自身平台凭证登录任一存在且启用的租户。
系统 MUST NOT 要求该平台账号的 `organization_id` 与登录企业编码对应租户相同。

#### Scenario：superAdmin 登录其它企业

- **GIVEN** 唯一平台 `superAdmin` 账号归属租户 A
- **AND** 租户 B 存在且启用
- **WHEN** `superAdmin` 使用租户 B 的企业编码和正确的平台密码登录
- **THEN** 登录成功
- **AND** 会话登录租户为 B

#### Scenario：superAdmin 不能登录停用租户

- **GIVEN** 目标租户已停用
- **WHEN** `superAdmin` 使用该租户企业编码登录
- **THEN** 系统返回与账号密码错误相同的 HTTP 401 失败响应

#### Scenario：普通用户仍校验所属租户

- **GIVEN** 普通账号归属租户 A
- **WHEN** 使用租户 B 的企业编码和该账号凭证登录
- **THEN** 系统拒绝登录
- **AND** 不泄露账号或租户是否存在

#### Scenario：平台账号不唯一时失败关闭

- **GIVEN** 数据异常导致系统查到零个或多个 `superAdmin` 用户主体
- **WHEN** 尝试以 `superAdmin` 登录任意租户
- **THEN** 系统拒绝认证
- **AND** MUST NOT 任取一个主体继续登录

### Requirement：企业开户只创建可登录管理员

`superAdmin` 创建企业时，系统 MUST 只创建企业主数据和一个归属新企业的可登录管理员账号。
管理员所需的登录凭证与本企业管理权限绑定属于账号组成部分；系统 MUST NOT 代建默认部门、其它账号或企业业务数据。
开户管理员 MUST 允许 `dept_id` 为空，表示企业尚未建立并分配部门。

#### Scenario：superAdmin 执行租户开户

- **GIVEN** `superAdmin` 已登录租户 A
- **WHEN** 创建租户 B
- **THEN** 系统创建租户 B 与一个归属 B 的管理员账号
- **AND** 该管理员可使用 B 的企业编码登录并管理 B
- **AND** 该管理员初始部门为空
- **AND** 系统不创建 B 的默认部门、岗位、标签、其它账号或业务数据
- **AND** 操作结束后请求上下文恢复为 A

#### Scenario：未登记业务接口不能跨租户

- **GIVEN** `superAdmin` 已登录租户 A
- **WHEN** 调用普通业务接口并尝试访问租户 B 的记录
- **THEN** 系统按租户 A 的上下文执行
- **AND** MUST NOT 读取、修改或删除租户 B 的记录

## MODIFIED Requirements

### Requirement：登录以企业编码确定租户

系统 MUST 以企业编码、工号和密码完成登录，并在所有成功会话中记录登录目标租户与租户类型。
普通用户的 `user_code` 在租户内唯一，其账号归属 MUST 与登录目标租户一致；`superAdmin` 使用唯一平台凭证，
可不受账号归属限制选择登录目标租户。登录失败 MUST 使用统一话术，MUST NOT 区分企业编码不存在与密码错误。

客户端只在登录时提交企业编码；后续接口 MUST 以服务端 TokenSession 保存的登录租户为准，MUST NOT 允许请求参数覆盖。

#### Scenario：不同租户同名普通工号

- **GIVEN** 租户 A 与租户 B 各有一个工号为 `admin` 的普通账号
- **WHEN** 分别以各自企业编码登录
- **THEN** 两个账号都能登录
- **AND** 各自会话的租户为其所属租户

#### Scenario：登录租户贯穿整个会话

- **GIVEN** 用户以企业编码 A 登录成功
- **WHEN** 使用该 Token 调用任意普通业务接口
- **THEN** 系统只使用会话中的租户 A 执行数据隔离
- **AND** 不要求客户端再次提交企业编码

#### Scenario：不泄露租户存在性

- **WHEN** 使用不存在的企业编码登录，或使用正确企业编码与错误密码登录
- **THEN** 系统返回相同的失败话术

### Requirement：角色只表达功能权限

系统 MUST 使角色只承载功能权限。账号编码精确为 `superAdmin` 的用户继续拥有 `*` 功能权限，
但该身份 MUST NOT 自动扩大普通业务数据范围；其普通业务数据范围等于本次登录租户。

#### Scenario：超级权限不扩大普通业务数据范围

- **GIVEN** `superAdmin` 以租户 A 登录并拥有 `*` 功能权限
- **WHEN** 查询素材、文件、课程或人员等普通业务数据
- **THEN** 权限校验通过
- **AND** 查询结果仍只包含租户 A 的数据

### Requirement：租户隔离由数据访问层强制执行

系统 MUST 对包括 `superAdmin` 在内的所有普通 HTTP 业务请求统一注入登录租户条件，并在 INSERT 时填充登录租户的
`organization_id`。只有设计中登记的显式平台操作、监管聚合、异步全平台任务和跨租户授权读取可在最小范围旁路。

#### Scenario：superAdmin 普通写入自动归属登录租户

- **GIVEN** `superAdmin` 以租户 A 登录
- **WHEN** 新增素材或完成文件上传
- **THEN** 新记录的 `organization_id` 为 A
- **AND** 不依赖请求体显式传递 `organization_id`

#### Scenario：旧会话缺少租户信息

- **GIVEN** 改造前的 `superAdmin` TokenSession 没有 `organizationId`
- **WHEN** 使用该 Token 访问受隔离业务接口
- **THEN** 系统失败关闭并要求重新登录
- **AND** MUST NOT 回退为全局旁路

## REMOVED Requirements

- 删除“`superAdmin` 会话在整个 HTTP 请求范围跳过全部租户条件”的要求。
- 删除“`superAdmin` 未指定租户时不写入会话租户、普通业务数据默认全局可见”的要求。
- 删除“平台级 `superAdmin` 普通业务操作因无租户归属统一记录 `organization_id=0`”的要求；成功登录及普通业务操作改记本次登录租户。
