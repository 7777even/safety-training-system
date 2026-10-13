# Tasks：将 superAdmin 绑定到登录租户

> 状态：**已完成（2026-08-10）**
>
> Owner：lims
>
> 前后端影响统一为：仅后端；前端以 OpenAPI 为准另开。
>
> 依赖顺序：Task 1 → Task 2 → Task 3。一次只执行一个 Task。

---

## Task 1：完成 superAdmin 选择登录租户能力

- 状态：已完成
- Owner：lims
- 输入：人工确认的登录规则；现有 `POST /api/auth/login`；唯一平台 `superAdmin` 用户及其现有 local 凭证
- 输出：携带登录租户的内部认证结果；普通用户与 `superAdmin` 分支认证；所有成功会话写入目标租户
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：请求 URL、Method、字段和响应结构不变；修订 `docs/api/system-admin.openapi.json` 的登录语义、错误说明与示例
- 数据影响：无 schema、索引和种子变更；复用现有平台凭证
- 验收标准：
  - [x] 普通用户只有在 `orgCode` 对应租户与账号 `organization_id` 相同时才能登录
  - [x] `superAdmin` 使用同一平台凭证可登录任一存在且启用的 `ENTERPRISE` 或 `REGULATOR` 租户
  - [x] `superAdmin` 不因其用户主体所属租户与目标租户不同而登录失败
  - [x] 所有成功会话均保存目标 `organizationId`、`orgType` 和独立的 `superAdmin` 标识
  - [x] 成功登录日志记录目标登录租户；失败话术继续防止枚举租户与账号
  - [x] 查到零个或多个平台 `superAdmin` 主体时认证失败关闭
- 测试方式：先修改 `LocalLoginStrategyTest`、`AuthApplicationServiceImplTest` 等零 DB 测试并观察失败；再以最小实现通过；
  运行受影响测试及 `mvn -pl sts-service-admin -am test`

---

## Task 2：完成 superAdmin 普通业务租户隔离能力

- 状态：已完成
- Owner：lims
- 输入：Task 1；TokenSession 中的登录租户；现有 `TenantContextFilter`、租户插件和普通业务接口
- 输出：`superAdmin` 请求绑定登录租户；移除请求级全局旁路；普通业务统一由租户插件隔离和填充
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无新增或删除接口；`X-Target-Tenant-Id` 仍仅用于监管租户只读下钻，`superAdmin` 使用该头返回 403
- 数据影响：无数据库结构变化；普通业务 INSERT 的 `organization_id` 改由登录租户上下文稳定填充
- 验收标准：
  - [x] `superAdmin` 登录租户 A 后，普通业务查询只能读取 A，按租户 B 的记录 ID 访问时不泄露数据
  - [x] `superAdmin` 登录租户 A 后新增文件、素材及其它普通业务记录，`organization_id` 自动写为 A
  - [x] Filter、Controller 和整个 HTTP 请求范围不再为 `superAdmin` 调用 `TenantContext.bypass()`
  - [x] `superAdmin` 携带 `X-Target-Tenant-Id` 返回 HTTP 403，不静默切换或忽略
  - [x] 缺少 `organizationId` 的旧 `superAdmin` 会话失败关闭，不回退到全局旁路
  - [x] 请求完成或异常后租户 ThreadLocal 被清理，不影响线程复用
- 测试方式：先修改 `TenantContextFilterTest` 及业务租户边界测试并观察失败；完成实现后运行零 DB；
  运行 `mvn -pl sts-service-admin -am -Pintegration-test test`，用 MySQL IT 覆盖文件、素材 SELECT/INSERT 的真实租户 SQL
  与非空约束

---

## Task 3：完成企业管理员开户与回归交付

- 状态：已完成
- Owner：lims
- 输入：Task 2；现有租户 CRUD 和开户逻辑；企业管理员可登录并管理本企业的要求
- 输出：开户只创建企业主数据和一个可登录企业管理员；移除旧局部兼容；契约、测试和工程记录同步
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：同步 `docs/api/system-admin.openapi.json`；租户 CRUD 外部 Contract 不变
- 数据影响：migration 将 `us_sys_user.dept_id` 调整为可空；创建新企业管理员及其必要凭证、权限绑定，不创建默认部门或业务数据
- 验收标准：
  - [x] `superAdmin` 登录任一租户后仍可执行企业查询、创建、修改和停用
  - [x] `requireSuperAdmin` 之类的调用者校验不受当前登录租户过滤影响，且只能识别唯一平台账号
  - [x] 开户事务只创建一个归属新企业的管理员及其登录凭证和必要权限绑定
  - [x] 开户管理员 `dept_id` 为 null；普通用户创建接口仍要求有效部门
  - [x] 开户不创建默认部门、岗位、标签、其它账号或任何企业业务数据
  - [x] 新管理员可使用新企业编码登录，登录后只能管理新企业；结束或异常后恢复 `superAdmin` 原登录租户
  - [x] 普通业务中为旧 `superAdmin` 请求旁路而增加的局部租户补丁被删除或收敛，不保留双重解析
  - [x] 全仓库生产代码中的 `callWithBypass` 均有明确用途；未登记的请求级旁路为零
  - [x] OpenAPI 明确“登录企业决定整个会话的数据租户”和 `superAdmin` 特殊归属规则，并满足四条铁律
  - [x] 双轴 Review 结论为通过，`tasks.md`、进度表、QA 与 Retro 按实际证据更新
- 测试方式：先修改 `OrganizationServiceImplTest` 和相关 MySQL IT 并观察失败，覆盖“仅一个管理员、无默认部门、可登录且租户正确”；运行
  `mvn -pl sts-service-admin -am test` 与 `mvn -pl sts-service-admin -am -Pintegration-test test`；按本 Change 验收场景回归

## 完成证据

- Admin 零 DB：556 个用例通过，0 失败、0 错误、0 跳过。
- Admin MySQL/MinIO 集成测试：198 个用例通过，0 失败、0 错误、0 跳过。
- `docs/api/system-admin.openapi.json`、`docs/api/org.openapi.json` JSON 校验通过。
- migration `V20260810151000__allow_user_without_department.sql` 已由真实 MySQL 用例验证。
- 规格符合性 Review：通过；代码质量 Review：通过。
