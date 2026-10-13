# M5 PC 监管闭环验收与菜单审计（2026-09-30）

> 更新说明：本文早期阶段记录中的“监管账号口令缺失、真实验收未完成”等结论已过时。最新验收结论、真实链路结果及剩余 Docker IT 限制以 [`2026-09-30-m5-pc-regulation-closeout.md`](2026-09-30-m5-pc-regulation-closeout.md) 为准；下文保留问题发现与修复过程。

> 结论：**M5 仍不能登记为端到端完成**。代码、静态检查、定向单测全部通过；监管角色与机构开户的真实登录验收因缺少监管账号口令未取得（见「未取得的运行证据」）。本轮额外发现两处菜单/权限收口问题（幽灵菜单 9752、死菜单 9532），均非本 Change 引入，但需要在关闭 M5 前处置。

## 一、环境事实（本轮实测）

- 后端 `http://localhost:20080` 在跑；前端 `http://localhost:5173` 返回 200。
- VPN 通（`19.191.192.146` 的 8045、16379 均可连），应用库为团队远程库 `sts`。
- `flyway_schema_history` 显示 `20260929120000 add m5 pc menus` **已于 2026-09-30 09:03:18 成功应用**（`2026-09-30-m5-pc-regulation-closeout.md` 中「新的 M5 菜单尚未应用」已过时）。
- 登录：`POST /api/auth/login`，鉴权头为 `Authorization: Bearer <token>`。`DEMO/admin` 可用既有摘要登录。

## 二、验证覆盖

| 项 | 命令 / 方式 | 结果 |
|---|---|---|
| OpenSpec | `openspec validate 2026-09-29-close-m5-pc-regulation --strict` | 通过 |
| PC typecheck | `vue-tsc --noEmit` | 通过（无输出） |
| PC 定向 ESLint | `eslint src/api/regulation.ts src/api/ops-workbench.ts src/router/admin-dynamic-routes.ts src/views/admin/regulation src/views/admin/ops src/views/admin/system/org/organization/index.vue --max-warnings=0` | 通过 |
| PC Node 契约测试 | `m5-regulation-contract` / `m5-ops-contract` / `organization-management` | 3/3 通过 |
| Admin 定向单测 | `-Dtest=SysDept,SysRole,OpsWorkbench,Organization,PlanHoursWarningRefresh,RegulationWorkbench` | `Tests run: 49, Failures: 0, Errors: 0` |
| Admin 编译 | touch 全量源码后 `mvn -pl sts-service-admin test` | `Compiling 1035 source files` + `303 test files` → 重编成功（非 `Nothing to compile`） |

**证据强度提醒**：三个 PC Node 测试是**源码字符串断言**（`readFileSync` + 正则匹配 API 路径与组件写法），不是运行时行为测试，不能替代真实端到端验收。后端 49 例为 Mockito 服务层单测。

## 三、真实 HTTP 复核（DEMO/admin 登录态）

| 请求 | 结果 |
|---|---|
| `GET /api/regulation/workbench` | HTTP 403 / `{"code":1003,"message":"权限不足: 无此权限：regulation:overview:view"}` |
| `GET /api/regulation/warnings/unread-count` | HTTP 403 / `1003` `regulation:warning:list` |
| `GET /api/regulation/learning-stats` | HTTP 403 / `1003` `regulation:learning:view` |
| `GET /api/org/organizations` | HTTP 403 / `{"code":4048,"message":"仅系统管理员或监管租户可管理租户"}` |

## 四、疑惑一：两处「园区监管」

真实库 `us_sys_menu` 里有两个顶级目录，路径同为 `/admin/regulation`：

| menu_id | 名称 | module_id | 角色绑定 | 来源 |
|---|---|---|---|---|
| **9752** | 园区监管（子项 9753 监管工作台 / 9754 数据中心 / 9755 学时预警 / 9756 机构管理 / 9757~9760 按钮） | `NULL` | **0 条** | **不在任何 migration、`menus.json`、`menus-back.json` 中**，属共享库手工残留 |
| **9810** | 园区监管（子项 9811/9812/9813/9814 + 按钮） | `9810` | 1 条：REGULATOR 的 `admin` 角色 | `V20260929120000__add_m5_pc_menus.sql` |

- 普通租户按角色绑定取菜单，因此**只看得到 9810**；已用 `GET /api/system/users/profile` 核对 DEMO/admin 的菜单树中 9752、9810 均为 0 次命中。
- `superAdmin` 在 `StpInterfaceImpl` 被授予 `*` 全局权限旁路，菜单不过滤绑定，因此**同时看到两个 `/admin/regulation`**，这正是截图 1 顶部出现两个「园区监管」标签、菜单管理列表列出两行的原因。
- 用户新建租户时复制菜单的语句是 `is_visible=1 AND module_id NOT IN (950, 9810)`，`module_id` 为 `NULL` 的 9752 树在三值逻辑下不满足 `NOT IN`，因此不会被复制；泄漏面仅限 superAdmin 视角。

**处置建议（需人工确认，属共享库写入）**：软删 9752 整棵树。

```sql
DELETE rm FROM us_sys_role_menu rm WHERE rm.menu_id BETWEEN 9752 AND 9760;
UPDATE us_sys_menu SET deleted = menu_id, is_visible = 0
 WHERE menu_id BETWEEN 9752 AND 9760 AND deleted = 0;
```

（9751 为 migration 建立的合法菜单「我的证书」，不在清理范围内。建议后续随一个幂等 migration 落地，避免手工执行。）

## 五、疑惑二：superAdmin 打开园区监管报 4065

**这不是缺陷，是刻意的入口收窄。**

`com.sts.regulator.service.RegulatorIdentityGuard#requireRegulator` 只放行会话租户类型为 `REGULATOR` 的请求，否则抛 `403 / 4065 跨租户读取仅监管平台租户可用`。类注释写明：`superAdmin` 同样拒绝，因为它虽有全局旁路，但监管能力是业务能力而非运维能力，入口保持单一。

`superAdmin` 会走到这个守卫、而 `DEMO/admin` 不会，原因是执行顺序：

1. `DEMO/admin` 先被权限拦截器拦下 → `1003 无此权限：regulation:overview:view`（已实测）。
2. `superAdmin` 在 `StpInterfaceImpl` 拿到 `*`，权限检查放行 → 落到 `RegulatorIdentityGuard` → 会话 `ORG_TYPE` 为 `ENTERPRISE` → `4065`。

**为什么是 ENTERPRISE**：`LocalLoginStrategy` 返回 `AuthenticationResult(userId, tenant.organizationId(), tenant.getOrgType())`，`AuthApplicationServiceImpl#fillTenantSession` 把该 `orgType` 写入会话；而 `superAdmin` 分支**不校验主体归属**，只把**登录时填写的企业编码**对应的租户写进会话。`superAdmin` 的归属机构是 `DEMO`（`ENTERPRISE`），所以按 DEMO 登录时 `ORG_TYPE=ENTERPRISE`。

**如何解决，两条路：**

- **推荐：改用监管租户身份验收。** 登录「企业编码」填 `REGULATOR`、账号用该租户下的管理员（库中 `regulatorAdmin`，角色 `监管管理员`，已绑定 9810/9814）。这是设计口径，无需改代码。
- **折中：`superAdmin` 登录时企业编码填 `REGULATOR`。** 由于 `superAdmin` 不校验主体归属、目标租户直接决定会话租户，这样 `ORG_TYPE=REGULATOR`，守卫会放行。等于「以监管平台租户身份」进入，与上一路等价。
- **若产品确实要求 `superAdmin` 在任意租户下都能看监管**：需要修改 `RegulatorIdentityGuard` 对会话 `SUPER_ADMIN` 标记放行。这与代码里现有注释的口径冲突，属需求决策，**须人工确认后再动**。

## 六、疑惑三：「机构管理」在园区监管与系统管理下各有一处

| 入口 | menu_id | 归属模块 | 授予对象 | 同一页面/权限码 |
|---|---|---|---|---|
| 园区监管 › 机构管理 | 9814 | 9810 | REGULATOR 的 `admin` | `system/org/organization/index`，`system:tenant:list` |
| 系统管理 › 机构组织 › 机构管理 | 9532 | 200（历史种子） | DEMO、M5QA_TI 的 `admin` | 同上 |

- 对**监管租户**：只绑了 9814，入口唯一，合理。监管用它在园区范围内查看、开通企业和培训机构。
- 对**企业/培训机构租户**：仍保留 9532，但 `OrganizationServiceImpl#requireOrgManager` 只放行 `superAdmin` 或监管租户用户，其他账号一律 `403 / 4048 仅系统管理员或监管租户可管理租户`。已实测 DEMO/admin 调 `GET /api/org/organizations` 得到 4048，且其 profile 确认持有 `system:tenant:list/add/edit` 并可见该菜单 —— **这是一个点了必然 403 的死菜单**。
- 该不一致**先于本 Change 存在**（本 Change 只删掉了「superAdmin 可创建监管租户」分支与 `assertRegulatorAbsent`，`requireOrgManager` 未改）。

**判定**：对监管租户合理；对非监管租户不合理。**处置建议（需人工确认）**二选一：

1. 从企业/培训机构 `admin` 角色收回 9532（及其子权限 1381/1382/1383），保持「管理租户」能力只属于平台侧与监管侧；
2. 或明确 9532 仅面向 `superAdmin`，由产品确认后从种子数据里改为不向普通租户分发。

**处置结果（2026-09-30 已执行，选定方案 1）**：

- 落点：新增幂等 migration `V20260930150000__unbind_legacy_organization_menu_from_tenant_roles.sql` 并在共享库执行
  （执行前导出受影响 `us_sys_role_menu` 8 行 → `.workbuddy/tmp/rollback-9532-role-menu-20260930.sql`）。
- 做法：只解绑非监管租户角色上的 9532 及 1381/1382/1383；**菜单节点 9532 不软删**（superAdmin 仍需经此维护租户主数据）；
  监管租户角色（role5）本就未绑，不受影响，仍走 9810 模块下的 9814。
- 为什么不能「只藏菜单、留权限」：权限查询 `SysMenuMapper#selectPermsByUserId` 同样带 `m.is_visible = 1`，
  菜单与权限共用同一套角色绑定，**无法分离**。
- 真实登录态复核（DEMO/admin）：
  - 菜单树「系统管理 › 机构组织」下只剩标签管理、岗位管理、部门管理，**9532 已消失**（对照：四页组织单元菜单仍正常）；
  - `GET /api/org/organizations` 由 `403 + 4048`（服务层）变为 **`403 + 1003 无此权限：system:tenant:list`**（Sa-Token 权限层）；
  - 需先清 `sts:cache:sys:user:perms::<userId>` 才生效 —— 权限缓存 TTL 30 分钟，**数据类 migration 不会自动失效缓存**，
    上线时须一并处理。
- 连带影响：企业侧 `canManageTenantScope`（`utils/tenant-context.ts`）由 true 变 false，`dept`/`position`/`role`/`tag`
  四页与 4 个选择器回退为「本会话租户、不显示跨租户机构下拉」—— 与该函数注释「该能力只应 superAdmin 拥有」一致，属修复而非破坏。
- **同步 seed 源**（不同步则新库 init 会重新绑上、死菜单重现）：`SeedDataFillerService` 原来把「除 9810 监控模块外」的
  所有菜单绑给 `role_code = 'admin'` 的角色，而 9532 的 `module_id = 200` 不在排除之列。已新增
  `TENANT_ADMIN_EXCLUDED_MENU_IDS`（9532/1381/1382/1383）一并排除；`data/menus.json` 中 9532 的**菜单定义保留**
  （superAdmin 仍需该入口维护租户主数据）。共享库 seed 不会重跑，故本次数据修正不受影响。
- 未执行：`FlywayMigrationIT.shouldUnbindLegacyOrganizationMenuFromTenantRolesOnUpgrade`（本机无 Docker），
  仅完成编译验证（`javap` 确认方法已入 class）；`SeedDataFillerService` 的改动同理**只能编译验证**——
  复现新库 init 需重建库。

## 七、M5 功能项完成度判定

- 功能项 1、2、3、4、7、8 的**代码与契约**已具备：机构分类与开户、监管概览与六项学习指标、机构/计划/参训人下钻、真实 CSV、学时预警查看闭环、企业工作台概览与分类待办。
- 前后端接口按 `regulation.openapi.json` / `ops.openapi.json` / `org.openapi.json` 对齐；旧 `/api/regulator/aggregates/tenants` 前端调用与内部路由已删除。
- **但** Change 自身的验收标准（监管可开通企业与培训机构、新管理员可登录、跨租户只读、CSV 与页面一致、预警幂等生成与计划修正后 ENDED、企业工作台真实数据跳转）**尚无一条取得真实登录态证据**，因此 M5 阶段不关闭。

## 八、未取得的运行证据（环境/凭据阻断）

- 监管租户 `regulatorAdmin` 的口令未知（非 `DEMO/admin` 摘要、非历史 `M4admin2026`），故监管侧登录未做。**请提供口令，或授权重置。**
- 机构开户后新管理员登录、部门/用户/角色维护、跨租户写入拒绝、园区与机构统计及 CSV 一致性、预警幂等与 ENDED 退出，均待上述身份取得后逐项复核。
- 本机无 Docker，`mvn -P integration-test` 的 Testcontainers 用例无法运行；本机无头 Chrome 不可用，浏览器级 Network 证据做不了。

## 九、需人工决策清单

- ~~是否执行 9752 幽灵菜单清理（共享库写入），以及是否落成幂等 migration。~~ → **已决策并执行**
  （2026-09-30：备份 → 软删 9 行 → 幂等 migration `V20260930110000`）。
- ~~非监管租户 `9532 机构管理` 的处理口径（收回 / 明确只面向 superAdmin）。~~ → **已决策并执行**：
  收回企业/培训机构角色绑定（`V20260930150000`），菜单节点保留给 superAdmin。见第六节「处置结果」。
- ~~是否允许 `superAdmin` 旁路 `RegulatorIdentityGuard`（当前代码口径是明确拒绝）。~~ → **已决策：不开放**。
  改为按会话租户类型收敛侧边栏（M5 Task 6）；superAdmin 需要监管能力时以 `REGULATOR` 编码登录。
- **仍待处理**：提供 `regulatorAdmin` 口令或授权重置，以便完成 M5 监管侧端到端验收。
- **新发现待定**：`OrganizationServiceImpl:153` 的 `notIn(SysMenu::getModuleId, 950L, 9810L)` 存在 NULL 三值逻辑隐患
  （会连带排掉 `module_id IS NULL` 的菜单）。属既有，未在本轮修改。

## 十、验收 5 问修复轮（2026-09-30 第二轮）

> 触发：M5 验收会提出的 5 个体验/权限问题。1～4 属展示口径（ID → 名称），5 属监管会话的导航渠道收敛。
> 结论：**1～4 完成并取得真实登录态证据；5 的后端字段与前端逻辑完成，验证到「字段真实回填 + ENTERPRISE 分支不误伤」，
> 监管分支（REGULATOR）仍因缺口令跑不了真实登录态。**
> **⚠️ 10.1 对问题 5 记的「完成」是过早的**：首版实现引入了一处回归（监管打开证书管理时侧栏整条消失），
> 已在 **10.5** 复核发现并修复。

### 10.1 逐条：现象 → 根因 → 改动

| # | 现象 | 根因 | 改动 |
|---|---|---|---|
| 1 | 培训计划「参训人」列只显示用户 ID | `TrainingParticipantVO` 未回填姓名 | 加 `@Translatable` + `@TranslateField(source="userId", type="USER_SERVICE") userName`；前端 `userName \|\| 用户 #id` 兜底 |
| 2 | 参训人列表右侧大片留白 | 数据列整列写死 `width`，ElTable 在无剩余列时不再拉伸 | 数据列改 `min-width`，仅操作列保留 `width="120"` |
| 3 | 标签/岗位/部门/用户/角色管理「所属机构」只显示机构 ID | 无机构名翻译能力，`OrgTagVO`/`OrgPositionVO`/`DeptTreeVO`/`RoleVO`/`UserVO` 均无 `orgName` | 新增 `ORG_SERVICE` 展示翻译链路（`CacheDomain.ORGANIZATION` + `DisplayOrganizationCacheRepository` + `OrganizationTranslationFeeder`），7 个 VO 补 `orgName` |
| 4 | 角色详情「所属租户」只显示 ID | 同上（`RoleVO`） | 详情弹窗改 `orgName \|\| organizationId` |
| 5 | 监管角色未勾「应用中心」，监管账号仍看到该顶部模块，点进去 403 | `V20260929120000` 为监管工作台只读快捷入口补了路由祖先菜单 9530；菜单树递归 CTE 向上补全父级 → 前端把任何 `menuType='D'` 当一级模块；页面要 `coursecategory:list`，监管固定权限集不含 | 前端按会话租户类型收敛导航（`REGULATOR_HIDDEN_ADMIN_MODULE_IDS = {9530, 9800}`）；profile 回填 `orgType`；监管工作台摘掉必然 403 的「课程制作」快捷入口 |

**问题 5 的第二层误导**：角色编辑弹窗「看着没勾」是渲染口径 —— `getSelectedLeafMenuIds` 只把叶子菜单算作已选，
而这 6 个菜单都有子节点，故渲染为未勾选，与库内事实相反；**不能凭弹窗勾选状态推断授权事实**。

**问题 5 的取舍**：只收敛导航，**不删任何授权数据**。监管快捷入口仍按 `certificate:cert:list` / `regulation:learning:view`
判定 `granted`，页面路由与权限码保持不变。

### 10.2 验证覆盖（本轮实测）

| 项 | 命令 / 方式 | 结果 |
|---|---|---|
| Admin test-compile | `mvn -pl sts-service-admin -am -DskipTests test-compile` | `EXIT=0` |
| Admin 定向单测 | `mvn -pl sts-service-admin -am test -Dtest=RegulationWorkbenchServiceTest,OrganizationServiceImplTest,OrganizationTranslationDisplayTest` | `Tests run: 23, Failures: 0, Errors: 0`，BUILD SUCCESS |
| PC typecheck | cwd `frontend/pc-web`，`vue-tsc --noEmit` | 通过（无输出，`EXIT=0`） |
| PC 定向 ESLint | 15 个改动文件 + `shared/src/types/api.ts`，`--max-warnings=0` | 通过 |
| PC Node 契约测试 | `tests/` 下 5 个契约测试（含新增「监管会话按渠道收敛后台导航」） | `tests 19 / pass 19 / fail 0` |
| OpenAPI JSON | 4 份 `docs/api/*.openapi.json` 走 `JSON.parse` | 全部合法 |
| 真实登录态 | 20081 + `vpn` profile，`DEMO/admin` 登录 → `GET /api/system/users/profile` | 200 / `code=0`；`orgName='示例企业'`、`orgType='ENTERPRISE'`、`deptName='发展管委会'` |

**验证踩坑（已闭环）**：`mvn -pl sts-service-admin test` 不加 `-am` 时，`sts-common` 从本地 `.m2` 解析到**旧 jar**，
运行期报 `NoSuchField CacheDomain.ORGANIZATION`，**看着像代码缺陷实为依赖陈旧**。加 `-am` 后 23 例全绿。

**关键回归判据**：`orgType` 必须来自**会话租户**而非账号归属。实测 `DEMO/admin` 得 `ENTERPRISE`，
且其菜单树仍含 `应用中心 / 企业工作台 / 系统管理` —— 证明渠道收敛只对 `REGULATOR` 生效，未误伤企业租户。

### 10.3 未取得的运行证据

- **监管分支（`orgType='REGULATOR'`）未跑真实登录态**：`regulatorAdmin` 口令仍未知。
  已实测以企业编码 `REGULATOR` + `DEMO/admin` 登录被拒（`401 / 1002 企业编码或账号密码错误`），
  证明 `LocalLoginStrategy` 对非 superAdmin 校验主体归属，**无法借道取得监管会话**。
  故 `REGULATOR_HIDDEN_ADMIN_MODULE_IDS` 的过滤效果只有源码契约断言 + 代码走查，不是运行时证据。
- **前端渠道收敛的浏览器级证据做不了**：本机无头 Chrome 不可用。

### 10.4 需人工决策

- ~~监管会话是否展示「应用中心 / 企业工作台」顶部模块。~~ → **已决策：不展示**（按渠道收敛导航，不删授权数据）。
  监管工作台的只读下钻改为路由载体，不再作一级入口；同时摘掉必然 403 的「课程制作」快捷入口。
- **仍待**：提供 `regulatorAdmin` 口令或授权重置，以补齐 M5 监管侧端到端验收。

### 10.5 复核发现：10.1 的问题 5 修复自身有一处回归（已修）

> 起因：文祥追问「`orgType` 到底有什么用、现在什么情况」，顺着消费点复核时发现的。
> **10.1 的表格把问题 5 记为「已完成」是过早的**，第一次实现有一半是坏的。

**事实（共享库实测 + 代码走查）**：

```sql
-- us_sys_menu 实测（deleted=0）
9530  应用中心    module_id=9530  menu_type=D  path=/admin/business-center
9700  培训成果    parent=9530     menu_type=M   path=/result        → 归一为 /admin/result
9703  证书管理    parent=9700     path=certificate
9706  已签发证书  parent=9703     path=certificates  ← module_id=9530
```

监管工作台保留的 `CERTIFICATE` 快捷入口 path 是 `/admin/result/certificate/certificates`，
**归属模块正是 9530 应用中心**。

**回归链**（`admin-dynamic-routes.ts` 首版实现）：

1. `getAuthorizedAdminModules` 加渠道过滤 → 监管会话的模块列表里**没有 9530**；
2. 但 `findAuthorizedAdminModule(userInfo, path)` 当时**也调 `getAuthorizedAdminModules`**（第 689 行）；
3. `/admin/result/...` **不落在任何模块的 URL 前缀**内（模块 path 是 `/admin/business-center`、`/admin/regulation`、`/admin/system`），
   只能靠函数尾部「按菜单树回溯」兜底 —— 而兜底要找的就是 9530；
4. 9530 已被滤掉 ⇒ 返回 `undefined` ⇒ `AdminLayout.vue` 的 `sidebarNavigation = activeModule?.children ?? []` 为空
   ⇒ **监管打开证书管理时整条侧栏消失**。

该行为在代码里本就有注释预警（原第 698~699 行）：
> 成果菜单…不落在任何模块的 URL 前缀内，但其菜单节点仍挂在应用中心下；此时按菜单归属回溯，**否则进入成果页面时整条侧栏会被隐藏**。

**根因是收敛的层级放错了**：「渠道收敛」的语义是「**不作为一级入口**」，不是「模块不存在」。
把它下沉到数据源（`getAuthorizedAdminModules`）会让所有依赖模块列表的能力一起被削弱。

**修法**：把收敛参数化，只作用于顶部导航展示。

```ts
function buildAdminModules(userInfo, applyChannelConvergence: boolean) { /* 过滤在此 */ }
export function getAuthorizedAdminModules(userInfo) { return buildAdminModules(userInfo, true); }   // 顶部导航
export function findAuthorizedAdminModule(userInfo, path) {
  const modules = buildAdminModules(userInfo, false);   // 归属查找：用完整列表
  ...
}
```

**影响面核对（确认没削到别处）**：

| 能力 | 数据源 | 是否受收敛影响 |
|---|---|---|
| 顶部导航模块列表 | `getAuthorizedAdminModules`（收敛） | 是（预期） |
| 当前页面归属模块 / 侧栏是否显示 | `findAuthorizedAdminModule`（未收敛） | **否（本次修复）** |
| 动态路由注册 | `synchronizeMenuRoutes` → `getResolvedMenuRoutes` | 否（本来就走未收敛） |
| 默认落地页 | `getDefaultMenuPath` | 否 |

**回归锁**：`tests/m5-regulation-contract.test.mjs` 新增用例「渠道收敛只作用于顶部导航，归属查找仍用完整模块列表」，
断言两处分别传 `true` / `false`。`tests 20 / pass 20`、`vue-tsc --noEmit` 通过、定向 eslint `--max-warnings=0` 通过。

**仍未验证**：该回归与修复都**没有拿到监管会话的运行时证据**（`regulatorAdmin` 口令未知），
证据强度是「共享库目录事实 + 代码走查 + 既有注释预警 + 源码契约测试」。**要用注册在册的监管账号走一遍才能结案。**

**顺带发现的遗留观感问题（既有，非本次引入，需人工定夺）**：修好之后监管打开证书管理，
侧栏会列出 9530 下监管**实际被授权**的全部菜单 —— 含 `300 课程管理`（页面要 `coursecategory:list`，监管拿不到 ⇒ 仍 403）。
即「侧栏不再整条消失，但其中可能夹着一条点了 403 的项」。
要彻底干净需把 `300/9531` 从监管角色解绑（共享库写入，须人工确认；注意「只解绑父菜单会被 F 子权限经递归拉回树里」这条硬约束）。

### 10.6 监管侧端到端闭环（第三轮，已取得真实登录态）

> 触发：文祥授权重置 `regulatorAdmin` 口令并要求「完整修复解决好」。
> 结论：**10.3 / 10.5 中「未取得监管运行时证据」的全部缺口本轮补齐**，并顺带清掉 10.5 末尾那条侧栏死入口。

#### A. 口令处置（已授权、可回滚）

口令不可读（`us_sys_auth_identity.credential` 是 BCrypt 单向哈希），只能重设。按登录校验链
（`LocalLoginStrategy:97` `passwordEncoder.matches(SHA-256(明文), credential)`）生成并写入：

- 目标：`identity_id = 8`（`identifier = REGULATOR:regulatorAdmin`，user 8 / org 1 监管平台）
- 写入值：`BCrypt(SHA-256(明文), rounds=10, prefix=2a)`，长度 60，落库复核头部一致
- **回滚快照**：`.workbuddy/tmp/rollback-regulatorAdmin-credential-20260930.sql`（原凭据原文）
- 明文与登录摘要：`.workbuddy/tmp/regulator-plain.txt` / `regulator-hash.txt`（`tmp/` 不入库）

#### B. 监管会话真实登录态（20081 新 jar，跑完即停，20080 未受影响）

| 检查 | 结果 |
|---|---|
| `POST /api/auth/login`（REGULATOR / regulatorAdmin） | `200 / code=0` ✅ 首次打通 |
| `GET /api/system/users/profile` | `orgType='REGULATOR'`、`orgName='监管平台'`、`userName='M5监管验收管理员'` ✅ |
| profile 顶层模块 | `["9530:应用中心","9810:园区监管","200:系统管理"]`（授权数据未删，由前端收敛） |
| `GET /api/regulation/workbench` | `200/code=0`，`shortcuts=["CERTIFICATE","DATA_CENTER"]` ✅ 收敛生效 |
| `GET /api/regulation/learning-stats` | `200/code=0` ✅ 监管只读接口放行 |
| `GET /api/regulation/training-plans` | `400/9860 须通过 X-Target-Tenant-Id 切换`（设计行为） |
| 对照 `DEMO/admin` | `orgType='ENTERPRISE'`，顶层仍含 `应用中心 / 企业工作台` ✅ 未被误伤 |
| 对照 企业会话打监管接口 | `403/1003` ✅ 双向隔离成立 |

#### C. 前端渠道收敛的**运行时**证据（补齐 10.3 的缺口）

本机无头 Chrome 不可用，改用 **Vite `ssrLoadModule` 真实执行** `src/router/admin-dynamic-routes.ts`，
输入是 **20081 dump 出来的真实菜单树**（不是手造 fixture）：

| 断言 | 结果 |
|---|---|
| 监管一级模块 = `["园区监管","系统管理"]` | ✅ 不含应用中心 / 企业工作台 |
| 企业一级模块仍含 `应用中心 / 企业工作台` | ✅ |
| 缺 `orgType`（旧缓存）时不收敛 | ✅ |
| 监管 `/admin/result/issued` 仍能定位归属模块（侧栏不消失） | ✅ 归属＝应用中心 |
| 归属模块侧栏一级项 | ✅ 修复后为 `["培训成果"]`（此前是 `["课程管理","培训成果"]`） |

代价与取舍：`AdminLayout.vue`、`PortalLayout.vue`、`utils/permission` 被 alias 成 stub（会牵出 element-plus 样式），
**被测函数本身未改动**；脚本为一次性验证产物（`.workbuddy/tmp/verify-regulator-channel.mjs`），不入库。

#### D. 死入口收口：解绑监管角色的课程管理祖先菜单

**证据**：监管会话实测 `course:category:list` = `403/1003`、`course:course:list` = `403/1003`（解绑后），
企业会话两者均 `200` —— 侧栏 `应用中心 › 课程管理 › 课程中心` 对监管是纯死入口。

**做法（关键：走服务层而不是直接改库）**：用监管账号自身（同租户、有 `sys:role:edit`）调
`PUT /api/system/roles/5`，`menuIds` 去掉 `300 / 9531`：

- `SysRoleServiceImpl.updateRole` 会执行 `userCacheService.evictAllPerms()` ⇒ **权限缓存自动失效**
  （直接改库做不到，`CacheDomain` 里根本没有权限缓存域，`POST /api/system/cache/clear` 也清不到 —— 这是 10.5 记的那个坑）
- 结果：绑定 `36 → 34` 条；重登后 `course:course:list` 由 `200` 变 `403` ⇒ 权限真正收回（判据符合技能里的「服务层码 → 权限层码」）
- 保留 `9530`：它是「培训成果 › 证书管理」只读下钻链路的父级载体
- 回滚快照：`.workbuddy/tmp/rollback-regulator-role-menu-20260930.sql`（36 条 INSERT）

**固化**：新增 `V20260930160000__unbind_course_management_from_regulator_role.sql`（按 `org_type='REGULATOR'` 收敛，幂等），
并完成三处同步：init `sts.init.schema-version`、`FlywayMigrationIT.INIT_SCHEMA_VERSION` → `20260930160000`，
6 处 `migrationsExecuted` 期望值 +1，新增用例 `shouldUnbindCourseManagementFromRegulatorRoleOnUpgrade`。
`AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION`（`20260924180000`）按约定**不同步**。

**seed 源核对**：`SeedDataFillerService` 的菜单绑定循环取的是**单条** `role_code='admin'`（`rs.next()`，即默认示例企业租户），
`TENANT_ADMIN_EXCLUDED_MENU_IDS` 里的 9532 等是「不发给企业管理员」，而 `300/9531` 恰恰是企业侧**必须保留**的入口
⇒ **本条不加入该排除集合**，seed 无需改动（新库不会在监管租户上重现该问题）。

#### E. 仍未完成 / 需人工决策

- **机构管理页两个监管下钻按钮**是否恢复：当初删除的理由（`orgType` 不存在、守卫恒 false）已不成立，属产品决策，本轮未动。
- **本机无 Docker**：新增的 IT 用例只做了 `test-compile` 验证，`-P integration-test` 跑不起来（Testcontainers 需要 Docker），
  没有真实跑过该用例；证据强度是「编译通过 + 手工在共享库执行同一 SQL 并复核」。
- **浏览器级渲染证据**仍无（无头 Chrome 不可用），前端结论来自 SSR 真实执行 + 真实菜单树。
- **口径变更需知会**：监管账号口令已被重置为测试口令（明文在 `tmp/`），若该账号在别处被使用需同步。

---

## 十一、衍生修复：监管角色的「标签/岗位」权限补齐（2026-09-30 第四轮）

### A. 现象与机制

监管会话打开「系统管理 › 机构组织 › 用户管理」，右上角连弹两条
`权限不足: 无此权限: org:tag:list` / `org:position:list`。

**根因不是「用户管理缺权限」，而是该页把标签/岗位管理的读权限当成了自己表单的下拉依赖**：
`views/admin/system/org/user/index.vue` 的 `onMounted` **无条件**执行
`Promise.all([loadUsers(), loadFormOptions()])`，而 `loadFormOptions` 并发拉 4 个下拉 ——
部门树 `sys:dept:list`、角色 `sys:role:list`、标签 `org:tag:list`、岗位 `org:position:list`。
监管角色有前两个（F 1021 / 1011），**一个 `org:*` 都没有**，故后两个 403。
（201 用户管理自身的 `sys:user:list` 监管是有的，页面本身能正常打开。）

### B. 口径判定：授权漏配，按其它租户同一套补齐

| 依据 | 事实 |
|---|---|
| 谁有这一套 | `9534`+`1511~1514`、`9535`+`1521~1524` 只授给了 role 1（示例企业 admin）与 role 6（M5 培训机构 admin） |
| 监管为何不同 | role 5 是**唯一**没有这套的管理员角色；但它的 `sys:user:*`(1001-1005)、`sys:role:*`(1011-1015)、`sys:dept:*`(1021-1024) 完整可写 ⇒ 它是本租户的组织管理员，侧栏也已有部门/角色/用户管理 |
| 数据佐证 | 监管租户（org1）已存在标签数据（1 条）；岗位表全表为空（任何租户都没建过岗位） |

**未采纳的两个替代口径及原因**：

1. **只授 F（1511/1521）**：菜单树查询是「向下授权 + **向上补父级**」递归（判据见
   `RbacPermissionQueryIT.selectAccessibleMenusByUserId` 的断言），只授 F 一样会把 9534/9535 拉进侧栏 ——
   既然本就要这两个菜单，就 C+F 一并授出，不留「菜单可见但按钮缺失」的半截状态。
2. **在「用户管理」(201) 下新增两个同 `perms` 的 F 节点**：能规避补父级副作用，但会让同一 perms code 出现两个 `menu_id`
   （角色编辑弹窗出现两个同名勾选项），并复现「按钮看着没勾却实际有权限」的困惑 ——
   与 `V20260929120000` 那次「监管没勾应用中心却能看到」是同一病灶，故弃用。

### C. 处置（已执行，可回滚）

- 共享库 `us_sys_role_menu` 中 role 5 绑定 **`34 → 44`**（+10），10 条明细已逐条核对。
- 固化 `V20260930170000__grant_tag_position_menus_to_regulator_role.sql`
  （按 `org_type='REGULATOR'` 解析 role_id；`us_sys_role_menu` 有 `uk_role_menu (role_id, menu_id)` 唯一键 ⇒ `INSERT IGNORE` 真正幂等）。
- 三处同步：init `sts.init.schema-version`、`FlywayMigrationIT.INIT_SCHEMA_VERSION` → `20260930170000`；
  **8 处** `migrationsExecuted` 期望值 +1（`11→12`、`19→20`、`9→10`、`6→7`、`5→6`、`4→5`、`4→5`、`1→2`，
  均为不带 `.target()` 的 `flyway(mysql)` 用例 —— 计数按「baseline 之后的迁移文件数」逐条重算，不是照抄 +1）；
  新增用例 `shouldGrantTagAndPositionMenusToRegulatorRoleOnUpgrade`。
  `AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION` 按约定**仍不同步**（保持 `20260924180000`）。
- **前端零改动**：补齐后 403 自然消失，侧栏「机构组织」下多出「标签管理」「岗位管理」两个**可用**菜单（与部门/角色管理平级）。
- **权限缓存**：**已按精确 key 核验**（见 D 末尾「方法学修正」）——
  `EXISTS sts:cache:sys:user:perms::8` = `1`，直接读值确认列表内已含
  `org:tag:list/add/edit/delete`、`org:position:list/add/edit/delete` 8 条，`TTL` ≈ 1241s，
  **即缓存中是新值、不存在陈旧缓存**，本次无需手工清理；
  上线时仍须按硬约束处理（数据类 migration 不失效权限缓存，TTL 30 分钟）。
- 回滚：`.workbuddy/tmp/rollback-regulator-tag-position-menu-20260930.sql`（精确删这 10 条）；
  改动后绑定快照 `.workbuddy/tmp/role5-bindings-after-20260930.txt`（44 条）。

### D. 运行时验证（2026-09-30 第五轮补齐，真实登录态）

前置：口令核验通过（`库内值长度=60`、`库内值 == 写入值 = True`、`bcrypt.checkpw = True`、`明文长度=14`），
覆盖前旧值已存 `.workbuddy/tmp/rollback-regulatorAdmin-credential-20260930T1645.sql`。
靶机 = 用户 IDEA 起的实例（`com.sts.AdminApplication`，PID 17980，`0.0.0.0:20080`），**本轮未另起后端**。
脚本 `.workbuddy/tmp/verify-regulator-perms-20260930.mjs`，口令经环境变量注入（不落盘、不进日志）。

| # | 断言 | 结果 |
| --- | --- | --- |
| 1 | `POST /api/auth/login`（`REGULATOR` / `regulatorAdmin`） | `http=200 code=0` |
| 2 | `GET /api/system/users/profile` | `http=200 code=0`，`orgName=监管平台 orgType=REGULATOR` |
| 3 | 顶层模块 vs 16:02 基线 | `["9530:应用中心","9810:园区监管","200:系统管理"]` → **完全一致** |
| 4 | 菜单节点数 / 集合 diff | `34 → 44`；新增 `[1511,1512,1513,1514,1521,1522,1523,1524,9534,9535]` **恰好等于目标 10 个**；消失 `[]` |
| 5 | `9534` / `9535` 节点属性 | `menuType=C`、`perms=org:tag:list` / `org:position:list`、各 4 个子节点 |
| 6 | 用户管理页 4 个下拉（`loadFormOptions`） | `depts/tree`、`roles`、`org/tags`、`org/positions` **全部 `http=200 code=0`** |
| 7 | 会话 `perms` 里的 `org:*` | 8 条：`tag`/`position` × `list/add/edit/delete` |

**before / after 闭环**：授权前快照 = 16:02 profile dump（`userinfo-regulator.json`，34 节点，**不含** 9534/9535）
与用户截图的两条 `权限不足: 无此权限: org:tag:list` / `org:position:list`；
授权后 = 上文 1~7。`us_sys_role_menu`（role 5）绑定对子数 `34 → 44`，`role_id` 全为 5，
相对可见树新增恰为目标 10 条、**无一条丢失**。

> **注（避免误读）**：脚本初版把顶层模块期望写成 `["园区监管","系统管理"]`（2 个），实测 3 个而报 `false`。
> 经比对 16:02 基线，`9530 应用中心` **当时就已存在** ⇒ 属**脚本期望值写错**，非本轮回归。
> `["…隐 9810 / 隐 9800"]` 那套收敛在 `SysMenuServiceImpl.getSidebarMenuTree()` 的 **superAdmin 分支**，
> 与本例的 **regulatorAdmin 租户会话**不是同一条路径，不能互相套用期望值。

### E. 仍未取得的证据（如实登记）

- **本机无 Docker** ⇒ 新 IT 用例只做到 `test-compile` + 字节码核对
  （`javap` 结果含 `shouldGrantTagAndPositionMenusToRegulatorRoleOnUpgrade`；因 `mvn` 未打印 `Compiling`，
  按本项目判据不以「BUILD SUCCESS」为证），**未真实执行**；**运行期 403→200 的翻转已在真实库上验到**，
  但「同一 SQL 由 Flyway 自动执行」这条链路仍未在容器内验证。
- **浏览器级证据仍缺**：本机无头 Chrome 不可用（`--dump-dom` 返回 0 字节）；
  故「进用户管理页不再弹两条红条」只由**接口级**（4 个下拉全 200）+ **菜单树级**（9534/9535 出现）共同支撑，
  未取到页面渲染截图。
- 现阶段该修复的证据强度 = **DB 级闭环 + 真实登录态接口级 + 编译级 + 契约级**。

### F. 方法学修正：远程 Redis 的 `--scan` 会给出**假阴性**

本轮曾以 `redis-cli --scan --pattern 'sts:cache:sys:user:*'` 得 **0 个 key** 并据此写下「缓存为空、无需清理」。
**该结论当时无效**：同一实例 `dbsize` = 25264，而 `--scan '*'` 在 25k key 上被超时截断（`SIGTERM`），
`--scan --pattern` 亦返回 0，属**假阴性**。改用**精确 `EXISTS`** 复核，同一 key 实为 **1**。

- 正确判据：`EXISTS <keyPrefix>:<cacheName>:<key>`，本项目 `<keyPrefix>` = `sts:cache`
  （`CacheProperties.keyPrefix`，`SpringCacheConfig` 用 `prefixCacheNameWith(keyPrefix + ":")`）
  ⇒ 权限缓存 key = `sts:cache:sys:user:perms::<userId>`；**不要用 `--scan` 判「有没有」**。
- 实际结论（权威）：`sts:cache:sys:user:perms::8` = `1`，值内含 8 条 `org:*`，`TTL` ≈ 1241s
  ⇒ 缓存里是**新值**，本轮确实无需清理（结论对，但**当初的论证过程不成立**）。
- 运维清缓存的正确入口是应用自带的缓存管理能力（菜单权限点 `sys:cache:list` / `sys:cache:clear`），
  而不是在客户端扫 key。

## 十二、衍生修复二：监管角色的「菜单管理」只读权限补齐（2026-09-30 第六轮）

### A. 现象与机制

**现象**：监管会话进入 `系统管理 › 机构组织 › 角色管理`，右上角弹
`权限不足: 无此权限: sys:menu:list`（实测 `403 / code=1003`），角色列表本身能正常渲染。

**机制**（与第十一节「标签/岗位」同型）：角色管理页在 `onMounted` **无条件**并发拉两个接口
（`frontend/pc-web/src/views/admin/system/org/role/index.vue`）：

```
onMounted(async () => { await Promise.all([loadRoles(), loadMenuTree()]); });
```

- `loadRoles()` → `GET /api/system/roles`，受 `sys:role:list` 保护 —— 监管**有**；
- `loadMenuTree()` → `GET /api/system/menus/tree`（`api/system-role.ts:122`），受 `sys:menu:list` 保护 —— 监管**没有**。

该菜单树不是装饰：`openCreate` / `openEdit` 弹窗用它渲染「角色权限勾选树」，
`menuIds()` 用 `getSelectedLeafMenuIds(menuTree, role.menuIds)` 反算叶子。
**即角色管理的核心功能（给角色配菜单权限）依赖它**，所以不可能靠「不打开弹窗就不拉」绕过。

### B. 系统性排查（不只看角色管理这一处）

不猜、直接实测：以监管会话把「监管可见的 11 个页面」的初始化依赖接口逐一打通，
脚本 `.workbuddy/tmp/audit-regulator-endpoints-20260930.mjs`（18 项，覆盖系统管理 5 页、应用中心 2 页、园区监管 4 页、公共 1 项）。

| 页面 | 初始化依赖 | 权限 | 修复前 |
| --- | --- | --- | --- |
| 用户管理 | users / depts/tree / roles / tags / positions | sys:user:list、sys:dept:list、sys:role:list、org:tag:list、org:position:list | 全 `200` |
| **角色管理** | roles + **menus/tree** | sys:role:list ✓、**sys:menu:list ✗** | **`403` 弹红条** |
| 部门管理 | depts/tree | sys:dept:list | `200` |
| 标签管理 / 岗位管理 | org/tags、org/positions | org:tag:list、org:position:list | `200` |
| 机构管理（9814） | org/organizations | system:tenant:list | `200` |
| 已签发证书（9706） | certificate/certificates + person-picker | certificate:cert:list ✓、**common:person:list ✗** | 列表 `200`，**姓名静默降级** |
| 证书规则（9705） | certificate/rules | certificate:rule:list ✗ | 菜单未授权 ⇒ **路由不注册、监管不可达** ⇒ 非问题 |
| 监管工作台 / 数据中心 / 学时预警 | regulation/* | regulation:* | `200` |

**结论：监管可见页面里，只有角色管理这一处会弹「权限不足」红条。**

### C. 口径判定：授权漏配，按其它租户同一套补齐

横向对照（与第十一节同一判据）：

- 缺 `sys:menu:*` 的**只有监管一家** —— 企业（role 1）与培训机构（role 6）都绑了 `204` 与 `1031-1034`；
- 监管在本租户有**完整可写**的 `sys:user:*` / `sys:role:*` / `sys:dept:*`，侧栏「系统管理 › 机构组织」下
  也已有部门 / 角色 / 用户管理 ⇒ 它本就是**本租户的组织管理员**。
- 据此判定为**授权漏配**，予以补齐；**不**把 `loadMenuTree()` 改成惰性 + 守卫
  —— 那只能让列表页不报错，角色配权限的功能仍然不可用，属治标不治本。

**但本次刻意只授「读」**（`204 菜单管理` + `1031 菜单查询`），**不授** `1032/1033/1034`：

- `us_sys_menu` 是**全局菜单定义**（无 `organization_id`），且 `SysMenuServiceImpl.getMenuTree`
  **不做租户过滤**（直接 `selectList` 全表后建树）；
- 一旦授出写权限，监管即可修改 / 删除**全系统**的菜单定义 —— 这是实质风险，不是风格问题；
- 监管侧真实需求只是「读菜单树给角色配权限」，故按**最小权限**只给读。

**已知且已确认接受的副作用**：菜单树查询是「向下授权 + **向上补父级**」递归，
授 `204` 后监管侧栏「系统管理」下会新增一个**只读**的「菜单管理」入口
（页面上的新增 / 编辑 / 删除按钮因 `canOperate('sys:menu:add'|'edit'|'delete')` 均为 `false` 而不渲染）。

> 替代口径（可做到侧栏零变化）已在决策时列出并**未被采纳**：
> ① 后端把 `/api/system/menus/tree` 放宽为 `sys:menu:list` **或** `sys:role:list` 任一即可；
> ② 后端新增角色管理专用的菜单树接口。二者都需改后端并另开 change。

### D. 处置（已执行，可回滚）

| # | 项 | 内容 |
| --- | --- | --- |
| 1 | 新增 migration | `V20260930180000__grant_menu_read_to_regulator_role.sql`（`INSERT IGNORE`，按 `org_type='REGULATOR'` 解析 role_id ⇒ 幂等） |
| 2 | 三处同步 ① | `sts-service-admin-init/.../application.yml` 的 `sts.init.schema-version` → `20260930180000` |
| 3 | 三处同步 ② | `FlywayMigrationIT.INIT_SCHEMA_VERSION` → `20260930180000` |
| 4 | 三处同步 ③ | IT 各用例 `migrationsExecuted` 按各自 baseline **重算**（不照抄 +1）：13 / 21 / 11 / 8 / 7 / 6 / 6 / 3 / 2 / 1；两个带 `.target(...)` 的用例仍为 1（不在影响范围） |
| 5 | 新增 IT 用例 | `shouldGrantMenuReadOnlyPermissionToRegulatorRoleOnUpgrade`（断言只授 204/1031、**不授** 1032-1034、既有 4 条保留、企业角色不受影响、重复迁移 `isZero`） |
| 6 | 回滚 | `.workbuddy/tmp/rollback-regulator-menu-read-20260930.sql` |

共享库人工执行（`main.flyway.enabled=false`，迁移默认不跑）：

```
role5 绑定 44 → 46，新增恰为 {204, 1031}
1032 / 1033 / 1034 命中数 = 0（未越权授出写权限）
```

**硬约束执行**：执行后清 `sts:cache:sys:user:perms::8`（`EXISTS` 1 → `DEL` → `EXISTS` 0），
否则监管用户 30 分钟内仍报 403（数据类 migration 不失效权限缓存）。

### E. 运行时验证（真实登录态，403 → 200 翻转）

靶机：本轮自起的 `20081`（`java -jar … --server.port=20081`，**验完已 kill**），
**未占用用户 IDEA 的 20080**；口令经环境变量注入、不落盘。

| # | 断言 | 修复前 | 修复后 |
| --- | --- | --- | --- |
| 1 | `GET /api/system/menus/tree`（角色管理依赖） | `403 code=1003` | **`200 code=0`** |
| 2 | 顶层模块 vs 16:02 基线 | — | `["9530:应用中心","9810:园区监管","200:系统管理"]` **一致** |
| 3 | 菜单节点集合 diff（`34 → 46`） | — | 新增 `[204,1031,1511,1512,1513,1514,1521,1522,1523,1524,9534,9535]`（上轮 10 + 本轮 2）；消失 `[]` |
| 4 | 会话 `perms` | 无 `sys:menu:list` | 含 `sys:menu:list`；`sys:menu:add/edit/delete` **全 false** |
| 5 | 侧边栏可见节点（非 F） | — | 17 个，相对修复前**仅新增 `204 菜单管理`** |
| 6 | 用户管理 / 标签 / 岗位 / 部门 / 机构 / 证书 / 监管 各页依赖 | 全 `200` | 全 `200`（无回归） |

脚本：`.workbuddy/tmp/audit-regulator-endpoints-20260930.mjs`、`.workbuddy/tmp/verify-menu-tree-after-20260930.mjs`；
修复后 profile dump 存 `.workbuddy/tmp/userinfo-regulator-after-menu-read.json`。

### F. 一并排查出、但**本次未处理**的两项（如实登记）

1. **「已签发证书」页的人员姓名静默降级为编号**（缺 `common:person:list`）。
   初始判定为「一并补授权」，**但复核后确认授权路线不可行**，两个理由：
   - 该权限节点是 `9553 人员选择`，父级为 `9531 课程中心`（C 型、有页面）、再上为 `300 课程管理`；
     授 `9553` 会被「向上补父级」带出 `9531` / `300`，**与 `V20260930160000`「监管课程管理菜单解绑」的决定直接冲突**；
   - `PersonPickerServiceImpl.pagePersons` 用 `.eq(SysUser::getOrganizationId, TenantContext.requireTenantId())`
     **按会话租户强过滤**（且完全忽略请求里的 `query.organizationId`）；监管会话只会返回 org1 的人员，
     而证书持证人是企业（org2/org3）学员 ⇒ **即便授权也查不到姓名**。
   - 正确修法是**后端展示名翻译**（easytrans，`@TranslateField(source="holderUserId", type="USER_SERVICE")`），
     属独立改动，**待决策**。
2. **「证书规则」接口 `403`**：`9705 签发规则` 未授权给监管 ⇒ 前端路由不注册，
   监管**根本进不到该页**，不构成用户可感知问题，不处理。

### G. 本轮踩到的两个环境坑（已沉淀进技能）

1. **全量 `test-compile` 首跑必失败**：删掉 `target/maven-status` 强制全量后，`default-testCompile`
   会在主源码 `target/classes` 尚未产出完毕时启动，报
   `SysUserControllerTest.java:[3,36] 程序包 com.sts.admin.aop.annotation 不存在` —— 而该包的 class
   （`BusinessType.class` / `OperLog.class`）**确实存在**于 `target\classes`。这是**编译时序**问题，与源码无关。
   规避：分两阶段跑 `mvn -pl sts-service-admin -am compile` 再 `mvn -pl sts-service-admin test-compile`，
   即可拿到 `Compiling 1038 source files` 与 `Compiling 304 source files` 两个 `BUILD SUCCESS`。
2. **增量编译会掩盖全量问题**：默认增量下只打印 `Nothing to compile - all classes are up to date`，
   按本项目判据（要见 `Compiling N source files`）**不算编译通过**，必须再用 `javap -p` 核对字节码；
   本次以 `javap` 确认 `shouldGrantMenuReadOnlyPermissionToRegulatorRoleOnUpgrade` 已进 class。
