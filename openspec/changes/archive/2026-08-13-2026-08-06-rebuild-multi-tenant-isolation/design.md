# Design：多租户隔离体系

## 1. 租户模型

`sts_org_organization` 由机构树降级为扁平租户主数据表，不再有层级：

| 字段                                       | 变化     | 说明                                      |
|------------------------------------------|--------|-----------------------------------------|
| `organization_id`                        | 保留     | 租户 ID，即全系统 `tenant_id`                  |
| `org_code`                               | 保留     | **企业编码**，登录入口标识，全局唯一                    |
| `org_name`                               | 保留     | 企业名称                                    |
| `org_type`                               | 收敛     | `ENTERPRISE`（企业租户）/ `REGULATOR`（监管平台租户） |
| `parent_organization_id`                 | **删除** | 不再有层级关系                                 |
| `contact_user_id`、`status`、`description` | 保留     | —                                       |

约束：

- `REGULATOR` 类型租户全系统 MUST 至多一个，由 migration 建立，不允许通过业务 API 再建第二个。
- 租户之间没有父子、包含或监管关系；跨租户可见性只由 `org_type` 决定。
- 租户创建后 `org_type` 与 `org_code` 不可修改。
- 停用租户后该租户全部账号不能登录，其数据对监管平台仍可读。

不保留 `PARK`、`TRAINING_INSTITUTION` 概念。培训机构如需入驻，按普通 `ENTERPRISE` 租户开户。

## 2. 租户上下文

### 2.1 上下文来源

```text
登录成功
  └─ Sa-Token Session 写入 organizationId、orgType
        ↓
每个请求：TenantContextFilter（Web 层，早于业务拦截器）
  ├─ 普通租户用户            → TenantContext = 自身 organizationId
  ├─ REGULATOR 用户 + 指定目标 → 校验通过后 TenantContext = 目标租户 ID
  └─ superAdmin              → 按显式指定；未指定时不注入租户条件
        ↓
请求结束 finally 清理 ThreadLocal
```

`TenantContext` 使用 `ThreadLocal`，必须在 `finally` 中清理，避免线程池复用串租户。

### 2.2 异步与非 Web 场景

定时任务、MQ 消费、异步转码回调没有登录态，MUST 由任务自身显式设置租户上下文后再执行业务逻辑，
禁止依赖"当前线程恰好有上下文"。异步线程池 MUST 配置上下文传递装饰器，不得继承调用方 `ThreadLocal` 的残留值。

## 3. 强制隔离机制

### 3.1 数据访问层统一注入

使用 MyBatis-Plus `TenantLineInnerInterceptor` + 自定义 `TenantLineHandler`：

- `getTenantId()` 返回 `TenantContext` 当前租户；上下文为空时 MUST 抛出错误而非返回 null，
  防止条件被静默省略后退化为全表查询。
- `getTenantIdColumn()` 返回 `organization_id`（复用现有列名，60 张业务表无需加列）。
- `ignoreTable()` 返回豁免表清单（§3.2）；`superAdmin` 旁路时对全部表返回 `true`。
- 上下文缺失时的分支：HTTP 请求线程返回 `false`（随后 `getTenantId()` 抛出，绝不生成无条件 SQL）；非 Web 线程（应用启动、集成测试）返回
  `true` 并留痕，否则框架自身的初始化查询会全部失败。

插件注册顺序：**租户插件 MUST 位于分页插件之前**，否则分页 `count` 语句不会带租户条件。

业务代码不再手写 `eq(Xxx::getOrganizationId, ...)`，也不再由 Service 自行解析归属；
INSERT 由插件自动填充租户列，业务不得显式 `setOrganizationId`（`REGULATOR` 代管场景除外，见 §4.3）。

### 3.2 表分类

判定原则：**承载业务数据的表一律带 `organization_id` 并纳入自动隔离，不留靠代码约定保护的表；
与业务无关的系统表和全局定义表保持全局共享**。数据清空重建，加列没有迁移成本；
插件在 INSERT 时自动填充，业务代码无需感知。

**A 类｜参与自动隔离，共 125 张**（Task 2 完成时 126 张，Task 3 删除 `sts_org_member` 后为 125）：

| 分组                        | 数量 | 说明                                                                                                                                                                                                                                      |
|---------------------------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 已带 `organization_id` 的业务表 | 60 | 保持不变                                                                                                                                                                                                                                    |
| 业务子表新增列                   | 61 | `sts_course_chapter`、`sts_course_item`、`sts_course_version`、`sts_question_option`、`sts_question_version`、`sts_exam_answer_item`、`sts_paper_version_question`、`sts_survey_answer`、`sts_training_plan_stage`、`sts_archive_version` 等全部从属表 |
| 系统表中承载租户数据的               | 4  | `us_sys_user`、`us_sys_file_info`、`us_sys_oper_log`、`us_sys_login_log`                                                                                                                                                                   |

补充处理：

- `us_sys_user` 唯一键改 `(organization_id, user_code, deleted)`。
- `us_sys_login_log` 登录成功时写入用户租户；渠道禁用、账号不存在、密码错误等无法解析租户的失败尝试记为 `0`，该值不属于任何租户，因此只有
  `superAdmin` 旁路能查到，符合安全审计预期。
- 新增列统一建单列索引 `idx_organization_id`；`us_sys_user` 的租户列已是唯一键 `(organization_id, user_code, deleted)`
  的最左前缀，不再额外建索引。

**B 类｜豁免，共 9 张**：

| 表                                     | 理由                                 |
|---------------------------------------|------------------------------------|
| `sts_org_organization`                | 租户主数据自身                            |
| `us_sys_menu`                         | 菜单为全局定义，租户通过角色授权菜单子集               |
| `us_sys_dict_type`、`us_sys_dict_data` | 系统字典，全平台统一维护                       |
| `us_sys_config`                       | 系统配置，全局生效                          |
| `us_sys_auth_identity`                | 登录阶段尚无租户上下文，隔离由 `identifier` 复合值保证 |
| `us_sys_role_menu`、`us_sys_user_role` | 纯关联表，两端主表均已隔离                      |
| `sts_common_resource_org_grant`       | **跨租户关系表**，见下                      |

`sts_common_resource_org_grant` 表达"A 租户把资源授权给 B 租户"，一行记录同时属于两个租户
（`owner_organization_id` / `grantee_organization_id`）。单一 `organization_id` 无法表达这个关系：
若按授权方隔离，被授权方就查不到授予自己的记录，跨租户资源共享能力直接失效。
因此该表豁免自动隔离，**改由业务代码显式按 `owner_organization_id` 或 `grantee_organization_id` 过滤**，
且两个方向的查询 MUST 各自带上当前租户条件，MUST NOT 无条件查询全表。

**删除，共 3 张**：`us_sys_role_dept`、`sts_org_regulatory_relation`、`sts_org_member`（见 §5.3）。

125 + 9 + 3 = 137，与改造前表总数一致，无遗漏。豁免名单 MUST 精确等于 B 类表。
新增豁免 MUST 在本文件记录理由并经 Review。

### 3.3 方法级豁免：递归 CTE

租户插件无法区分 `WITH RECURSIVE` 的 CTE 别名与真实表，会向递归引用注入
`别名.organization_id`，而该结果集并无此列，直接产生 SQL 语法错误。
`SysMenuMapper.selectAccessibleMenusByUserId`（菜单树递归上溯）因此使用
`@InterceptorIgnore(tenantLine = "true")` 做方法级豁免。

该豁免安全的前提：查询以 `ur.user_id` 精确定位，用户只能取到自己绑定的角色，
而"用户与其角色同租户"由 §5.4 的一致性校验保证。

新增含 CTE 或复杂派生表的自定义 SQL 时 MUST 先验证插件改写结果；
需要方法级豁免的 MUST 在此处登记并说明隔离由什么保证，MUST NOT 静默加注解。

### 3.4 隔离完整性

全部业务表带租户列后，不存在"直接按子表 ID 访问绕过隔离"的通道，业务代码无需再靠
"先查主表再校验归属"的约定保护。子表由插件在写入时自动填充租户，读取时自动过滤。

代价与约束：

- 子表租户值 MUST 与其主表一致。插件按当前上下文填充，正常路径天然一致；
  数据订正、批量导入等旁路 MUST NOT 手工写入与主表不符的租户值。
- IT MUST 覆盖"主表与子表租户一致性"，防止旁路写入造成孤儿数据。

### 3.5 登录路径的豁免

`us_sys_user` 进入隔离后，登录时按企业编码 + 工号查用户会因无上下文而失败。登录链路
MUST 使用 `InterceptorIgnoreHelper` 或 `@InterceptorIgnore(tenantLine = "true")` 显式豁免，
并在方法内自行以解析出的租户 ID 作为查询条件，禁止无条件查询。

## 4. 监管平台

### 4.1 可见范围

`REGULATOR` 租户用户无条件可读全部 `ENTERPRISE` 租户数据，不依赖任何监管关系配置。
`sts_org_regulatory_relation` 表删除。

### 4.2 下钻明细：切换视角

监管方查看某企业明细时，**不为业务接口增加"监管模式"分支**，而是切换请求的租户上下文：

```text
GET /api/exam/attempts?...        Header: X-Target-Tenant-Id: 42
  └─ TenantContextFilter 校验：当前用户所属租户 org_type == REGULATOR
        └─ TenantContext = 42
              └─ 业务 Service 与 SQL 完全不变，自动过滤到租户 42
```

由此 58 个 Controller、16 个业务域的查询代码无需感知监管逻辑。

约束：

- 非 `REGULATOR` 用户提交 `X-Target-Tenant-Id` MUST 返回 HTTP 403，且 MUST NOT 静默忽略该头继续以自身租户执行。
- 目标租户不存在或已删除 MUST 返回 HTTP 404/400，且不泄露其它租户信息。
- 切换视角的每次请求 MUST 记录审计日志（监管人、目标租户、访问资源）。

### 4.3 跨租户汇总

"所有企业的完成率排行"这类横向聚合无法用单租户上下文表达，走独立通道：

- 单独的监管统计 Service，方法级 `@InterceptorIgnore(tenantLine = "true")`，SQL 中显式 `GROUP BY organization_id`。
- 该通道 MUST 只提供只读聚合，MUST NOT 复用于写操作。
- 入口 MUST 校验 `REGULATOR` 身份，且 MUST 位于独立包路径便于审计。

### 4.4 跨租户被授权资源的读取

跨租户资源共享（`sts_common_resource_org_grant`）与自动隔离存在结构性冲突：
租户插件会过滤掉其它租户的资源，因此"先查资源、再校验授权"的既有写法在隔离开启后
**授权分支永远走不到**，跨租户共享静默失效。

统一解法是两段式读取（`CrossTenantResourceReader`）：

```text
1. 按当前租户查 → 命中即本租户资源，直接返回
2. 未命中 → 旁路查一次
     ├─ 仍为空 → 资源不存在
     └─ 查到   → 该资源属于其它租户，MUST 立即 requireAccess 校验授权
```

约束：

- 第 2 步返回的对象只说明"资源存在"，MUST NOT 视为有权访问；
  调用方 MUST 在拿到非本租户资源后立即执行授权校验，否则这里就是越权通道。
- 该读取器 MUST 只用于确实支持跨租户授权的资源类型（课程、考试、题库、培训标准/模板等），
  MUST NOT 用于普通业务查询。
- 已接入：课程详情、课程内容项引用考试、培训类型/标准/模板引用。

### 4.5 监管租户的业务概念

`REGULATOR` 租户不承载课程、考试、培训等业务。本期**只在菜单与权限码层面屏蔽**：不为该租户分配业务菜单与业务权限码。
不在各业务 Service 内增加"监管租户禁止创建"的物理拦截——16 个域逐一加分支收益低于成本。

风险：若误配权限码，监管租户可在自身租户内创建业务数据。缓解：种子数据固定监管角色权限集，并由测试断言其不含业务写权限。

## 5. 登录与账号

### 5.1 登录流程

```text
POST /api/auth/login  { orgCode, userCode, password }
  ├─ 1. 按 orgCode 查租户，校验存在且启用           → 失败：企业编码或账号密码错误（统一话术）
  ├─ 2. identifier = "{orgCode}:{userCode}"
  ├─ 3. us_sys_auth_identity 查 identity_type='local' 且 identifier 匹配
  ├─ 4. 校验密码（前端 SHA-256 + 后端 BCrypt，沿用现有实现）
  └─ 5. StpUtil.login(userId)，Session 写入 organizationId、orgType
```

`us_sys_auth_identity` 表结构不变，`uk_identity_type_identifier_deleted` 保持全局唯一——
`identifier` 改为复合值后天然满足租户内唯一。

登录失败 MUST 使用统一话术，MUST NOT 区分"企业编码不存在"与"密码错误"，避免枚举租户。

### 5.2 账号定位

- `user_code` 为租户内工号，唯一键 `(organization_id, user_code, deleted)`。
- `phone` 保持可空、可重复，不作为登录凭证。
- 短信登录等其它 `identity_type` 本期不实现；将来引入时 `identifier` 需同样保证全局唯一。

### 5.3 删除机构成员表

`sts_org_member` 的核心职责是用 `(organization_id, user_id)` 表达"用户属于哪个机构"，
该职责被 `us_sys_user.organization_id` 完全取代；一人一租户确定后，"用户"与"机构成员"不再需要拆成两个概念。
该表删除，档案字段并入 `us_sys_user`：

| 原字段                                        | 处置                                    |
|--------------------------------------------|---------------------------------------|
| `organization_id`、`user_id`                | 删除，由 `us_sys_user.organization_id` 表达 |
| `employee_no`                              | 删除，`user_code` 即租户内工号，不保留两个工号字段       |
| `member_type`（INTERNAL/EXTERNAL/REGULATOR） | 并入 `us_sys_user`，用于区分内部员工与外包/外部人员     |
| `email`、`job_title`、`entry_date`           | 并入 `us_sys_user`                      |
| `status`、审计字段                              | 丢弃，`us_sys_user` 已有                   |

连带改动（**必须同步，否则标签与岗位能力失效**）：

- `sts_org_member_tag`、`sts_org_member_position` 的外键由 `org_member_id` 改为 `user_id`，
  唯一键相应改为 `(user_id, tag_id, deleted)` 与 `(user_id, position_id, deleted)`；两表仍属 A 类，加 `organization_id`。
- `AudienceResolveServiceImpl` 的 `SUBJECT_TAG`、`SUBJECT_POSITION` 解析改为直接返回 `user_id`，去掉
  member → user 的中间映射。
- `PersonPickerServiceImpl` 的标签筛选改按 `user_id` 过滤。
- `ExamCandidateProfileSnapshotAssembler` 的考生档案快照改从 `us_sys_user` 取 `job_title` 等字段。
- `TrainingReferenceResourceServiceImpl` 的成员引用改为用户引用。

标签与岗位主表 `sts_org_tag`、`sts_org_position` **保留**：它们支撑参与范围的动态解析
（按标签/岗位圈定培训与考试对象）与选人组件筛选，是跨部门跨岗位交叉分类的唯一表达方式。

### 5.4 一致性校验

- 用户的 `dept_id` 所属部门 MUST 与用户 `organization_id` 一致。
- 用户绑定的角色 MUST 与用户 `organization_id` 一致。
- 以上校验在创建、修改用户和分配角色时执行，违反返回 HTTP 400 与稳定业务码。

## 6. 权限设计

### 6.1 角色数据范围整体删除

租户隔离已由数据访问层保证，角色不再承担可见性职责：

- 删除 `us_sys_role.data_scope` 字段与 `us_sys_role_dept` 表。
- 删除 `DataScopeService`、`DataScopeServiceImpl`、`DataScopeContext` 及其调用点。
- 角色只保留功能权限（菜单/权限码）语义。

### 6.2 三类主体

| 主体           | 判定依据                       | 能力                 |
|--------------|----------------------------|--------------------|
| 普通租户用户       | `org_type = ENTERPRISE`    | 仅本租户，由插件强制         |
| 监管平台用户       | `org_type = REGULATOR`     | 本租户 + 全部企业租户只读（§4） |
| `superAdmin` | `user_code = 'superAdmin'` | 全局旁路，含租户 CRUD      |

`superAdmin` 的旁路是账号级特权，不是角色配置项，沿用现有判定。

### 6.3 租户开户

租户 CRUD 仅 `superAdmin` 可调用，作为运维接口：

- 创建租户同时初始化该租户的默认部门、管理员账号与内置角色。
- 监管平台租户不通过该接口创建，由 migration 建立。
- 权限码 `system:tenant:*`，MUST NOT 授予任何普通角色。

## 7. Schema 变更方式

人工已确认清空业务数据重来，且本仓库没有 Flyway/Liquibase 自动迁移机制
（`database/migration/` 为手工执行脚本，`backend/sql/schema-*.sql` 是 schema 真相源并由 POM 拷贝供 IT 使用）。
因此**直接修改建表语句，不写增量 ALTER 迁移**：

1. 在 `backend/sql/schema-*.sql` 中直接为 64 张表（60 张业务子表 + 4 张系统表）的 `CREATE TABLE`
   加入 `organization_id bigint NOT NULL` 及对应联合索引。
2. `us_sys_user` 的唯一键直接改写为 `(organization_id, user_code, deleted)`。
3. `sts_org_organization` 直接删除 `parent_organization_id` 列、`DEFAULT` 占位 INSERT，
   `org_type` 注释收敛为两值。
4. 直接删除 `sts_org_regulatory_relation`、`us_sys_role_dept`、`sts_org_member` 的建表语句
   与 `us_sys_role.data_scope` 列；`us_sys_user` 加入 `member_type`、`email`、`job_title`、`entry_date`；
   `sts_org_member_tag`、`sts_org_member_position` 的外键改为 `user_id`。
5. 提供一个重建脚本 `database/migration/`：DROP 全部表 → 按新 schema 重建 → 写入 `REGULATOR` 租户、
   其部门、角色与管理员账号。
6. 种子数据（`sts-service-admin-init`）按新结构重写，`local` 凭证 `identifier` 直接以 `{orgCode}:{userCode}` 写入。

该脚本会删除全部数据，MUST 在文件头显著标注，且 MUST NOT 在存在真实数据的环境执行。
本期无生产环境；将来上线后的迁移方案另行制定，不属于本 Change。

## 8. API Contract

- 登录接口新增 `orgCode` 必填参数，失败话术统一。
- 角色接口删除 `dataScope`、`customDepts` 字段与相关 schema；业务码 2030/2031/2032 从契约移除。
- 机构接口改为租户接口：删除 `parentOrganizationId`，`orgType` enum 改两值，标注仅 `superAdmin` 可调用。
- 知识点、题库分类、档案导出、证书规则等接口删除 `organizationId` 请求参数。
- 新增监管平台契约：跨租户聚合接口、`X-Target-Tenant-Id` 头的定义与 403 错误契约。
- 全部改动遵循四条铁律：按 Controller 分类、接口注释、字段注释、example。

## 9. 测试策略

- **零 DB**：`TenantLineHandler` 的租户解析与空上下文报错、监管切换视角的身份校验与 403、
  登录租户解析与统一失败话术、账号租户内唯一校验、部门/角色一致性校验、`superAdmin` 旁路。
- **Controller/WebSlice**：登录请求新增 `orgCode` 的契约、角色接口删除字段后的请求响应、
  删除 `organizationId` 参数后的接口签名、`X-Target-Tenant-Id` 越权返回真实 403。
- **MySQL IT**（关键，隔离正确性只有真库能证明）：
  - 插件对 SELECT/UPDATE/DELETE/INSERT 的条件注入，含分页 `count` 语句。
  - 跨租户按 ID 直查、直改、直删一律无结果或拒绝。
  - 监管切换视角后读到目标租户数据、切回后读不到。
  - 跨租户聚合通道返回多租户分组数据。
  - `us_sys_user` 租户内唯一键：两租户可同名工号，同租户不可重复。
  - 登录链路豁免后仍带租户条件，不会全表命中。
  - 子表按 ID 直查跨租户记录无结果；主表与子表租户值一致。
- 各业务域 MUST 执行目标查询回归，MUST NOT 以仅验证插件配置替代业务查询回归。

## 10. 风险与取舍

- **上下文泄漏是本方案最大风险**：`ThreadLocal` 未清理或异步未传递会导致读到其它租户数据。
  缓解：统一 Filter 的 `finally` 清理、线程池装饰器、IT 覆盖线程复用场景。
- **空上下文必须失败而非放行**：若 `getTenantId()` 返回 null 被框架当作"不加条件"，会退化为全库查询。
  缓解：显式抛错 + IT 断言。
- **豁免名单是持续风险面**：每加一张豁免表就是一个潜在越权点，豁免 MUST 在 design 记录理由并经 Review。
- **子表租户值与主表不一致**：正常写入路径由插件保证一致，但数据订正、批量导入等旁路可能写错，
  造成子表读不到或读到错租户。缓解：IT 覆盖一致性、旁路写入统一走带上下文的服务方法。
- **监管租户靠权限配置约束**：§4.4 未做物理拦截，误配权限即失效，依赖种子数据与测试保证。
- **不支持一人多租户**：本期账号与租户强绑定；将来若需跨企业任职，登录与账号模型需再次调整。
- **单库共享表**：租户数据量差异大时无法单独扩展；本期规模下接受，分库分表非本期目标。
