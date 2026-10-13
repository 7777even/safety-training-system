# organization-master-data Specification

## ADDED Requirements

### Requirement: 机构是园区、企业和培训机构的统一主数据

系统 MUST 使用 `sts_org_organization` 统一保存平台、园区、企业和培训机构主体，并通过 `org_type` 区分
`PLATFORM`、`PARK`、`ENTERPRISE`、`TRAINING_INSTITUTION`。部门、成员、监管关系、资源授权及业务聚合 MUST
引用稳定的 `organization_id`，不得以机构名称或前端临时值代替机构 ID。

#### Scenario: 登记真实企业

- **GIVEN** 操作人具有 `org:organization:add` 权限
- **WHEN** 提交唯一机构编码、企业名称和 `ENTERPRISE` 类型
- **THEN** 系统创建真实机构记录并返回机构 ID
- **AND** 后续部门、成员和业务数据可以引用该机构 ID

#### Scenario: 区分园区与企业

- **GIVEN** 系统中同时存在园区和企业
- **WHEN** 查询机构列表
- **THEN** 两者均作为机构返回
- **AND** 分别以 `PARK` 和 `ENTERPRISE` 类型区分
- **AND** 系统不得仅凭机构父子关系推导监管关系

### Requirement: 机构编码、类型和层级必须稳定有效

机构编码 MUST 全局唯一且创建后不可修改。机构类型 MUST 为已确认的四类之一且创建后不可修改。上级机构 MUST 存在且启用，机构层级
MUST NOT 形成自引用或循环。

#### Scenario: 拒绝重复编码

- **GIVEN** 已存在未删除机构编码 `ACME`
- **WHEN** 再次创建编码为 `ACME` 的机构
- **THEN** 系统返回 HTTP 409 和业务码 4041
- **AND** 不创建重复记录

#### Scenario: 拒绝循环层级

- **GIVEN** 机构 B 已是机构 A 的后代
- **WHEN** 尝试把 A 的上级设置为 B
- **THEN** 系统返回 HTTP 409 和业务码 4044
- **AND** 原层级保持不变

### Requirement: 机构登记册必须同时执行功能权限与数据范围

机构查询、创建和编辑 MUST 分别校验 `org:organization:list`、`org:organization:add` 和
`org:organization:edit`，并按当前权限解析既有 `data_scope`。`ALL` 普通管理员 MUST 能够查询、创建和完整维护非默认机构；
本机构及更窄范围 MUST 只能查询主机构，并只能修改其名称、联系人和描述。除账号编码精确等于 `superAdmin` 的既有旁路外，系统
MUST NOT 根据角色名称、角色级别或机构类型授予隐含权限。

#### Scenario: 无权限账号不能查询机构登记册

- **GIVEN** 已登录账号不具有 `org:organization:list`
- **WHEN** 请求机构分页或详情
- **THEN** 系统返回 HTTP 403
- **AND** 不返回机构主数据

#### Scenario: ALL 普通管理员创建机构

- **GIVEN** 普通管理员具有 `org:organization:add` 权限
- **AND** 对该权限解析出的 `data_scope=ALL`
- **WHEN** 提交合法的新机构
- **THEN** 系统允许创建机构
- **AND** 不要求账号为 `superAdmin`

#### Scenario: 本机构管理员只能修改白名单字段

- **GIVEN** 机构管理员具有 `org:organization:edit` 权限
- **AND** 对该权限解析出的范围仅包含其主机构
- **WHEN** 修改本机构名称、联系人或描述
- **THEN** 系统允许更新
- **WHEN** 尝试修改机构上级或状态
- **THEN** 系统返回 HTTP 403 和业务码 4049

#### Scenario: 部门或本人范围退化到主机构

- **GIVEN** 当前权限的数据范围为 DEPT、DEPT_AND_CHILDREN、CUSTOM 或 SELF
- **WHEN** 查询机构登记册
- **THEN** 系统只返回有效机构成员所属主机构
- **AND** 部门范围继续用于人员和业务数据过滤，不把机构主数据拆分为部门记录

### Requirement: 默认占位机构不得继续承担新增真实业务

`org_code=DEFAULT` 的机构 MUST 仅用于兼容既有历史归属。机构登记册默认 MUST NOT 返回该记录，且任何修改 MUST 被拒绝。新增部门、成员、
监管关系、跨机构授权和业务聚合 MUST NOT 选择该记录作为真实机构。

#### Scenario: 拒绝修改默认机构

- **GIVEN** 目标机构编码为 `DEFAULT`
- **WHEN** 操作人尝试编辑或变更其状态
- **THEN** 系统返回 HTTP 409 和业务码 4046
- **AND** 默认机构数据保持不变

### Requirement: 禁用机构必须保护引用和历史

系统 MUST 在禁用机构前检查有效部门、成员、监管关系、资源授权和未结束业务实例。存在有效引用时 MUST 拒绝禁用。禁用后的机构 MUST
保留历史查询和快照，但 MUST NOT 再用于新增组织关系或业务数据。本期 MUST NOT 提供机构删除接口。

#### Scenario: 存在有效成员时拒绝禁用

- **GIVEN** 目标机构存在未删除且启用的机构成员
- **WHEN** 操作人请求禁用机构
- **THEN** 系统返回 HTTP 409 和业务码 4045
- **AND** 机构仍保持启用

#### Scenario: 禁用无引用机构

- **GIVEN** 目标机构不存在有效组织关系和未结束业务实例
- **WHEN** 具有编辑权限的操作人提交正确版本号并请求禁用
- **THEN** 系统将状态更新为禁用
- **AND** 后续新增业务不得选择该机构
