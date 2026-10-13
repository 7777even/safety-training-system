# Spec Delta：多租户隔离体系

## ADDED Requirements

### Requirement：租户是系统的基本隔离单元

系统 MUST 以租户作为唯一数据隔离单元。每个企业是一个 `ENTERPRISE` 租户，另有至多一个 `REGULATOR` 监管平台租户。
租户之间 MUST NOT 存在层级、包含或父子关系。租户 `org_code` MUST 全局唯一，创建后 `org_code` 与 `org_type`
MUST NOT 修改。

#### Scenario：企业租户互不可见

- **GIVEN** 租户 A 与租户 B 各自拥有课程、考试、素材和人员
- **WHEN** 租户 A 的用户查询任意业务列表
- **THEN** 结果只包含租户 A 的数据
- **AND** 租户 B 的数据不出现在列表、详情、统计或导出中

#### Scenario：监管平台租户唯一

- **GIVEN** 系统已存在一个 `REGULATOR` 租户
- **WHEN** 通过业务 API 再创建 `REGULATOR` 租户
- **THEN** 系统拒绝创建

#### Scenario：仅运维可开户

- **GIVEN** 当前用户不是 `superAdmin`
- **WHEN** 调用租户创建或停用接口
- **THEN** 系统返回 HTTP 403
- **AND** 不写入租户记录

#### Scenario：开户初始化归属新租户

- **WHEN** `superAdmin` 创建企业租户
- **THEN** 系统同时初始化该租户的默认部门、内置角色与管理员账号
- **AND** 初始化数据全部归属新租户

### Requirement：租户隔离由数据访问层强制执行

系统 MUST 在数据访问层统一注入租户条件，MUST NOT 依赖各业务代码自行编写机构过滤。
当租户上下文缺失时，系统 MUST 抛出错误，MUST NOT 省略租户条件继续执行。
客户端参数 MUST NOT 指定或扩大数据归属范围。

#### Scenario：跨租户按 ID 越权

- **GIVEN** 目标记录属于其它租户
- **WHEN** 用户直接提交该记录 ID 查询、修改或删除
- **THEN** 系统拒绝访问或返回空结果
- **AND** 不泄露目标记录的业务内容

#### Scenario：分页统计同样受限

- **GIVEN** 系统内存在多个租户的业务数据
- **WHEN** 用户查询分页列表或统计汇总
- **THEN** 分页总数与统计数值只基于本租户数据计算

#### Scenario：上下文缺失不得放行

- **GIVEN** 当前线程没有租户上下文
- **WHEN** 执行受隔离的数据访问
- **THEN** 系统抛出错误
- **AND** MUST NOT 生成不含租户条件的全表查询

#### Scenario：线程复用不串租户

- **GIVEN** 上一请求由租户 A 用户发起且已结束
- **WHEN** 同一线程处理租户 B 用户的请求
- **THEN** 该请求只能读到租户 B 的数据

#### Scenario：异步任务显式声明租户

- **GIVEN** 定时任务或消息消费没有登录态
- **WHEN** 执行涉及业务数据的处理
- **THEN** 任务 MUST 先显式设置目标租户上下文
- **AND** 产物不含其它租户数据

### Requirement：登录以企业编码确定租户

系统 MUST 以企业编码、工号和密码完成登录，并在会话中记录当前租户与租户类型。
登录账号 `user_code` MUST 在租户内唯一，MUST NOT 要求全局唯一。
登录失败 MUST 使用统一话术，MUST NOT 区分企业编码不存在与密码错误。

#### Scenario：不同租户同名工号

- **GIVEN** 租户 A 与租户 B 各有一个工号为 `admin` 的账号
- **WHEN** 分别以各自企业编码登录
- **THEN** 两个账号都能登录
- **AND** 各自会话的租户为其所属租户

#### Scenario：同租户工号冲突

- **GIVEN** 租户 A 已存在工号 `admin`
- **WHEN** 在租户 A 再创建工号 `admin` 的账号
- **THEN** 系统拒绝创建

#### Scenario：不泄露租户存在性

- **WHEN** 使用不存在的企业编码登录，或使用正确企业编码与错误密码登录
- **THEN** 系统返回相同的失败话术

#### Scenario：停用租户不能登录

- **GIVEN** 租户已停用
- **WHEN** 该租户下任意账号登录
- **THEN** 系统拒绝登录

### Requirement：用户的部门与角色必须与所属租户一致

系统 MUST 保证用户所属部门和绑定角色与用户租户一致。

#### Scenario：拒绝跨租户部门

- **WHEN** 将用户的所属部门设置为其它租户的部门
- **THEN** 系统返回 HTTP 400
- **AND** 不写入变更

#### Scenario：拒绝跨租户角色

- **WHEN** 为用户绑定其它租户的角色
- **THEN** 系统返回 HTTP 400

### Requirement：监管平台可跨租户查看全部企业数据

`REGULATOR` 租户用户 MUST 能查看全部 `ENTERPRISE` 租户的业务明细与统计，且 MUST NOT 依赖任何监管关系配置。
跨租户访问 MUST 只读，MUST 记录审计日志。非 `REGULATOR` 用户 MUST NOT 通过任何请求参数获得跨租户可见性。

#### Scenario：监管方下钻企业明细

- **GIVEN** 当前用户属于 `REGULATOR` 租户
- **WHEN** 指定目标企业租户后查询其考试记录或培训档案
- **THEN** 系统返回该企业的业务明细
- **AND** 记录监管人、目标租户与访问资源的审计日志

#### Scenario：监管方跨租户汇总

- **GIVEN** 系统内存在多个企业租户
- **WHEN** `REGULATOR` 用户查询跨租户统计
- **THEN** 系统按租户分组返回全部企业的汇总数据

#### Scenario：普通用户不能切换租户

- **GIVEN** 当前用户属于 `ENTERPRISE` 租户
- **WHEN** 请求中携带目标租户标识
- **THEN** 系统返回 HTTP 403
- **AND** MUST NOT 忽略该标识后按自身租户返回数据

#### Scenario：监管方不能写入企业数据

- **GIVEN** `REGULATOR` 用户已切换到目标企业租户视角
- **WHEN** 尝试创建、修改或删除该企业的业务数据
- **THEN** 系统拒绝该写操作

## MODIFIED Requirements

### Requirement：角色只表达功能权限

系统 MUST 使 `us_sys_role` 只承载功能权限（菜单与权限码）语义。角色 MUST NOT 承担数据可见性职责，
数据可见性一律由租户隔离决定。账号编码精确等于 `superAdmin` 的全局旁路保持不变。

#### Scenario：角色不再配置数据范围

- **WHEN** 创建或修改角色
- **THEN** 请求与响应均不包含数据范围或自定义部门字段
- **AND** 角色的可见数据边界等于其所属租户

## REMOVED Requirements

- 删除机构层级模型：`PLATFORM`、`PARK`、`TRAINING_INSTITUTION` 类型与 `parent_organization_id` 上下级关系。
- 删除六类角色数据范围 `ALL`、`ORGANIZATION`、`DEPT`、`DEPT_AND_CHILDREN`、`CUSTOM`、`SELF`
  及 `us_sys_role.data_scope` 字段、`customDepts` API 字段和 `us_sys_role_dept` 表。
- 删除 `DataScopeService` 数据范围解析能力，其职责由数据访问层租户隔离承担。
- 删除机构监管关系 `sts_org_regulatory_relation`；监管可见性改由租户类型决定。
- 删除 `DEFAULT` 占位机构与 `DefaultOrganizationService` 默认归属机制。
- 删除业务接口中由客户端指定 `organizationId` 的能力。
- 删除机构成员档案 `sts_org_member` 与独立的"机构成员"概念；用户归属由 `us_sys_user.organization_id` 表达，
  `member_type`、`email`、`job_title`、`entry_date` 并入用户，`employee_no` 由 `user_code` 取代。
  标签与岗位改为直接关联用户，按标签、按岗位圈人的能力保持不变。
