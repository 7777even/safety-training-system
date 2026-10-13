# Design：superAdmin 登录租户绑定

## 1. 核心语义

系统将“功能权限”和“数据租户”拆成两个独立维度：

| 维度       | 普通用户       | `superAdmin`    |
|----------|------------|-----------------|
| 功能权限     | 由角色与菜单授权决定 | 保持 `*` 全量功能权限   |
| 登录租户     | 必须等于账号所属租户 | 可选择任一存在且启用的租户   |
| 普通业务数据范围 | 登录租户       | 登录租户            |
| 企业开户     | 不允许        | 创建企业主数据与一个企业管理员 |

企业编码只在登录时提交。登录成功后，服务端把解析出的目标租户写入 TokenSession；后续接口只提交 Token，
不得接受客户端重复提交企业编码来覆盖会话租户。

## 2. 登录流程

```text
POST /api/auth/login { identityType: local, authParams: { orgCode, userCode, password } }
  ├─ 1. 解析 orgCode 对应租户，并校验存在且启用
  ├─ 2. userCode == "superAdmin"
  │     ├─ 全局、精确定位唯一的 superAdmin 用户主体与其 local 凭证
  │     ├─ 校验密码和账号状态
  │     └─ 不校验用户 organization_id 是否等于目标租户
  └─ 3. 其它用户
        ├─ 按 {orgCode}:{userCode} 定位 local 凭证
        ├─ 校验密码和账号状态
        └─ 强制校验用户 organization_id 等于目标租户
          ↓
认证结果 = userId + loginTenantId + loginTenantType
          ↓
TokenSession = organizationId + orgType + superAdmin
```

### 2.1 平台凭证定位

`superAdmin` 继续使用现有唯一用户主体和现有 `local` 凭证，不复制到每个企业，也不创建企业级同名账号。
认证时先在租户旁路范围内按账号编码精确定位平台用户，再按 `user_id + identity_type=local` 读取凭证。
若查到零个或多个 `superAdmin` 主体，必须失败关闭，禁止任取一个。

`us_sys_auth_identity.identifier` 的现有值无需迁移；普通用户仍以 `{orgCode}:{userCode}` 定位。
应用层继续禁止创建或修改出第二个 `superAdmin` 账号。

### 2.2 认证结果边界

登录策略不能只返回 `userId`，否则认证应用服务会把平台账号自身的归属租户误当成登录租户。
内部认证结果 MUST 同时携带已校验的目标租户 ID 与类型，认证服务只能使用该结果写会话和成功登录日志，
不得再次从请求字符串独立推导租户。

### 2.3 失败语义

- `orgCode` 不存在、目标租户停用、账号不存在、凭证不存在和密码错误继续统一返回 HTTP 401、业务码 1002、
  `企业编码或账号密码错误`。
- 参数缺失继续返回 HTTP 400、业务码 1001。
- 已定位账号被禁用继续返回 HTTP 403、业务码 1003。
- 失败日志只有在目标租户已安全解析时才可记录目标租户；其余情况沿用 `0`，不得通过错误差异泄露租户是否存在。

## 3. 会话与请求上下文

所有登录成功的 TokenSession，包括 `superAdmin`，MUST 写入：

- `organizationId`：登录企业编码解析出的租户 ID；
- `orgType`：该目标租户的类型；
- `superAdmin`：仅表示功能权限身份，不表示租户旁路。

`TenantContextFilter` 的规则调整为：

```text
未登录请求                    → 不绑定租户，禁止访问受隔离业务表
普通用户且无目标租户请求头      → 绑定 TokenSession.organizationId
superAdmin 且无目标租户请求头   → 绑定 TokenSession.organizationId
REGULATOR 用户且有合法目标头    → 沿用既有只读视角切换
其它身份携带目标租户请求头       → HTTP 403
```

旧 TokenSession 缺少租户信息时必须失败关闭，不允许把 `superAdmin` 恢复成全局旁路。部署后旧会话通过重新登录刷新。

## 4. 普通业务与企业开户

### 4.1 普通业务接口

素材、文件、课程、考试、用户、角色等普通业务 Service 不感知 `superAdmin`。所有 Mapper 的查询和写入均由
`TenantContext` 注入当前登录租户；INSERT 的 `organization_id` 由插件自动填充。

已存在的“因为 `superAdmin` 请求处于旁路而手工切回会话租户”的局部兼容代码应删除或收敛，避免形成第二套租户解析逻辑。

### 4.2 企业开户边界

企业主数据表是租户插件豁免的全局表，`superAdmin` 可在通过身份校验后查询、创建、修改和停用企业。
创建企业后，开户事务只额外创建一个可登录的企业管理员：

- 在 `callWithTenant(newTenantId, ...)` 的最小范围内写入管理员用户及其 local 登录凭证；
- 管理员所必需的权限绑定属于该管理员账号的组成部分，只能授予本企业管理权限；
- 管理员初始 `dept_id=NULL`，表示尚未分配部门；`us_sys_user.dept_id` 通过 migration 调整为可空；
- 不创建默认部门、岗位、标签、其它账号或任何业务数据；这些由管理员登录目标企业后自行维护；
- 开户事务完成或异常后恢复 `superAdmin` 的原登录租户。

按用户 ID 校验调用者是否为唯一 `superAdmin` 时，可以在最小范围读取平台账号主体，避免该主体被当前登录租户过滤。
这只是服务端身份校验，不赋予后续业务代码跨租户数据权限。

禁止在 Controller、Filter 或整个 HTTP 请求范围调用 `TenantContext.bypass()`。原多租户设计已登记的异步全平台扫描、
跨租户授权读取和监管聚合不因本 Change 改变；新增旁路仍须另行登记用途、访问表和权限护栏。

## 5. 审计规则

- `superAdmin` 登录成功日志记录所选登录租户，不记录平台账号自身归属租户。
- `superAdmin` 的普通业务操作日志记录 TokenSession 的登录租户。
- 企业开户仍以当前登录租户作为操作者会话上下文；新企业 ID 由业务参数与日志内容留痕。
- 无法解析目标租户的失败登录继续记 `organization_id=0`。

本 Change 不增加审计表字段。若后续需要把“操作者登录租户”和“平台操作目标租户”结构化为两个独立列，另开数据库 Change。

## 6. 安全护栏

- `superAdmin` 判定继续要求账号编码精确等于大小写敏感的 `superAdmin`，不得使用角色名或客户端字段判定。
- 普通账号即使拥有全部权限码，也不能跳过账号归属与登录租户一致性校验。
- `superAdmin` 登录目标租户必须存在且启用；停用租户不能作为登录入口。
- `X-Target-Tenant-Id` 不作为 `superAdmin` 的租户切换机制。
- 平台旁路结束后必须恢复原登录租户上下文，异常路径同样必须恢复。

## 7. 兼容与部署

- 外部登录 URL、HTTP Method 和请求字段不变，仅修正权限与会话语义。
- 数据库只将 `us_sys_user.dept_id` 调整为可空，不改表意外的字段、索引或种子数据，也不新增生产依赖。
- 部署后清理或失效旧 `superAdmin` Token；若不主动清理，首次访问也必须因缺少租户信息失败并要求重新登录。
- 前端路径式入口由前端协作方实现；后端只要求最终登录请求中的 `orgCode` 与路径企业编码一致。

## 8. 测试设计

按 Red → Green → Refactor 执行：

1. 零 DB 认证测试先证明：普通用户跨企业失败、`superAdmin` 使用同一平台凭证可登录不同启用租户、停用目标失败、认证结果携带目标租户。
2. 零 DB 会话与 Filter 测试先证明：`superAdmin` 会话写入目标租户、请求绑定租户而非旁路、非法目标头返回 403、旧会话失败关闭。
3. 零 DB 开户服务测试先证明：调用者校验可读取唯一平台账号；开户只创建一个企业管理员及其必要认证授权数据，
   不创建默认部门或业务数据；结束后恢复原租户。
4. MySQL IT 证明：`superAdmin` 登录租户 A 后，普通业务 SELECT/INSERT 只作用于 A；不能读 B；文件与素材写入自动得到 A 的
   `organization_id`；新企业管理员能登录并只管理新企业。
