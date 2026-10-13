# Tasks：M5 PC 监管闭环

> 状态：用户已确认范围和口径。按 Task 顺序一次实施一个，最多连续三个。

## Task 1：机构开通与 M5 入口对齐

- 状态：完成；机构开户、管理员登录/部门/角色维护、停用拒登及菜单迁移已在测试环境验收；Flyway MigrationIT 因本机 Docker 不可用未运行。Owner：fxw11。
- 输入：功能项 4；现有机构 Controller、OpenAPI、菜单及 PC 机构页。
- 输出：机构授权口径入主规格；开户契约修正；M5 菜单归入后台；PC 机构分类、开户、编辑、启停和只读进入；旧监管汇总入口删除。
- 前后端影响：PC 页面/API、后端快捷路径与菜单；不新增权限模型。
- API 依赖：`org.openapi.json`；既有机构增删改查和监管只读目标租户协议。
- 数据影响：只调整既有菜单路径和 init 快照，新增幂等 migration 与 schema-version；不改业务表。
- 验收标准：监管可开通企业和培训机构；新管理员可登录；无权用户无法开户；停用后不可登录；监管进入目标机构仅可读。
- 测试方式：先补定向失败用例，再做最小实现；PC Node 测试、typecheck、定向 lint、菜单/Flyway IT、真实登录核查。

## Task 2：园区监管工作台与学习统计

- 状态：完成；监管真实登录、园区与机构统计下钻及 CSV 数据已验收。Owner：fxw11。
- 输入：功能项 1、3、7；Task 1 路由与机构分类；后端监管聚合和学习统计事实。
- 输出：监管概览、类型与特种作业、机构/计划/参训人下钻、六项学习指标、机构/人员/开课明细及真实 CSV。
- 前后端影响：PC 监管页面/API；后端快捷路径与 OpenAPI 示例仅在路径不符时同步。
- API 依赖：`regulation.openapi.json` 的 workbench、training-plans、learning-stats 及导出。
- 数据影响：无业务表变化。
- 验收标准：两机构园区汇总和机构明细同口径；零分母为 0；机构只读视角退出后不泄漏；CSV 与页面一致。
- 测试方式：Red → Green → Refactor；PC 定向测试、typecheck、lint，监管真实登录和导出验收。

## Task 3：培训计划学时预警查看闭环

- 状态：完成；预警幂等生成、首次查看留时、修正后 ENDED 与再次不足重开均已验收。Owner：fxw11。
- 输入：功能项 2；现有自动刷新、预警证据和 CSV API。
- 输出：待查看数量、列表筛选、详情对比、来源计划及导出；不加人工处置。
- 前后端影响：PC 监管页面/API；既有后端行为不改。
- API 依赖：`regulation.openapi.json` 的 warnings、unread-count、详情、导出和培训计划只读下钻。
- 数据影响：无。
- 验收标准：不足计划出现一条待查看；重复保存不重复；查看记录时间；补足后 ENDED 退出默认列表；CSV 可打开。
- 测试方式：Red → Green → Refactor；PC 定向测试、typecheck、lint，企业与监管真实登录验收。

## Task 4：企业管理员工作台

- 状态：完成；企业管理员真实登录、待办计数与快捷入口跳转已验收。Owner：fxw11。
- 输入：功能项 8；既有企业概览和分类待办 API。
- 输出：权限化快捷入口、真实概览、分类待办及业务列表跳转。
- 前后端影响：PC 企业页面/API；后端快捷路径与 OpenAPI 示例按实际路由同步。
- API 依赖：`ops.openapi.json`。
- 数据影响：无。
- 验收标准：无权入口不显示；待办分类之和等于总数；卡片跳转到真实列表。
- 测试方式：Red → Green → Refactor；PC 定向测试、typecheck、lint，企业管理员真实登录验收。

## Task 5：双轴 Review、QA 与 M5 状态收口

- 状态：完成；规格符合性/代码质量 Review、监管与企业真实登录验收及进度记录已完成。MigrationIT 未运行（Docker 不可用），实际 Flyway migration 已在用户确认的测试库应用并核验。Owner：fxw11。
- 输入：Task 1～4 结果及监管、企业管理员真实登录证据。
- 输出：规格符合性与代码质量 Review、QA、进度表和必要 Retro。
- 前后端影响：只记录真实交付状态。
- API 依赖：Controller/OpenAPI、前端消费点、菜单权限一致。
- 数据影响：核对 migration/init 结果。
- 验收标准：所有主链真实登录通过后才从“后端完成”更新为端到端完成；未实测项明确记录。
- 测试方式：OpenSpec strict、`git diff --check`、定向测试及真实角色浏览器验收。

## Task 6：后台专属模块按会话租户类型收敛可见性

- 状态：完成；定向单测通过，监管与企业会话的真实菜单树已验收。Owner：fxw11。
- 输入：Task 1 的 M5 菜单与机构口径；`RegulatorIdentityGuard` 的会话租户类型判据。
- 输出：侧边栏菜单按会话租户类型隐藏专属模块——非监管会话隐藏园区监管（9810），监管会话隐藏企业工作台（9800）；不开放监管入口授权旁路。
- 前后端影响：后端菜单树单点收敛，PC 与移动端零改动；不改授权判定、API 结构与数据库。
- API 依赖：无新增；`UserVO.menus` 内容按会话租户类型收窄，字段与错误码不变。
- 数据影响：无。
- 验收标准：超级管理员以企业编码登录时侧边栏无园区监管；以监管编码登录时侧边栏无企业工作台；无归属模块（module_id 为空）的菜单不受影响；普通用户按角色绑定的结果不变。
- 测试方式：Red → Green → Refactor；`SysMenuServiceImplTest` 定向单测；真实双身份登录验收待环境与账号。

## Task 7：清理「机构管理」重复菜单在非监管租户的误绑

- 状态：数据已修正并完成真实登录态验证，IT 用例已补（本机无 Docker 未执行）；Owner：fxw11。
- 输入：Task 1 的机构授权口径——机构（`sts_org_organization`）是租户主数据本身，只由 superAdmin 或监管租户维护
  （`OrganizationServiceImpl#requireOrgManager`）；M5 已在园区监管模块下建立正式入口 `9814 机构管理`。
- 输出：解绑企业（org2）与培训机构（org3）管理员角色上误绑的 `9532 机构管理` 及其按钮权限 `1381/1382/1383`；
  菜单节点 9532 保留不软删（superAdmin 仍需借此维护租户主数据）；监管租户角色独立绑定不受影响。
- 前后端影响：仅菜单/权限数据，**不改代码、不改 API、不改授权判定**；前端侧边栏随之少一个死入口。
  init 侧同步修 `SeedDataFillerService` 的角色菜单绑定排除项（否则新库初始化会重现该死菜单）。
- API 依赖：无新增。解绑后企业侧 `system:tenant:list/add/edit` 权限关闭，`canManageTenantScope`
  回退为会话租户——与 `utils/tenant-context.ts` 注释「该能力只应 superAdmin 拥有」一致。
- 数据影响：新增幂等 migration `V20260930150000__unbind_legacy_organization_menu_from_tenant_roles.sql`；
  同步 `sts.init.schema-version`、`FlywayMigrationIT.INIT_SCHEMA_VERSION` 与各用例 `migrationsExecuted` 期望值。
- 验收标准：企业/培训机构会话侧边栏不再出现「系统管理 › 机构组织 › 机构管理」；访机构列表由 403/`4048`
  变为 403/`1003`（Sa-Token 权限层先于服务层）；监管租户走 9814、superAdmin 走 `*` 短路均不受影响；重复执行幂等。
- 测试方式：新增 `FlywayMigrationIT.shouldUnbindLegacyOrganizationMenuFromTenantRolesOnUpgrade`（Docker 环境执行）；
  真实登录态已验证（DEMO/admin 菜单树无 9532、机构列表返回 `1003`）；`git diff --check`。
- 开户路径补漏（2026-09-30，用户确认）：`OrganizationServiceImpl` 为新企业/培训机构建立内置 `admin` 角色时，
  同样排除 9532、1381、1382、1383；不改 API、授权判定或既有机构数据。前后端影响：仅后端默认角色菜单绑定；
  输入为本 Task 的租户主数据权限边界，输出为新开户默认授权与 init/迁移口径一致。
  验收：两类新机构保留租户内管理菜单，不获得机构主数据管理权限；先用定向测试复现 4 条误绑，再修复并通过
  `OrganizationServiceImplTest` 17 项及 `git diff --check`。运行时新开户登录复验仍需部署本次代码后执行。

## Task 8：监管侧端到端验收闭环与监管课程管理祖先菜单解绑

- 状态：代码与数据均已完成，**已取得监管账号真实登录态证据**；IT 用例已补（本机无 Docker 未执行）；Owner：fxw11。
- 输入：Task 5 / 6 遗留的两项缺口 —— ① 监管分支（`REGULATOR`）无任何真实登录态证据（口令不可得）；
  ② 监管侧栏在 `应用中心 › 课程管理 › 课程中心` 存在点击必然 403 的死入口（页面要 `course:category:list`，监管从未被授予）。
- 输出：
  - 经授权重置 `regulatorAdmin` 口令，打通监管会话真实登录态（原凭据已导出可回滚）。
  - 解绑监管角色上为「课程制作」快捷入口补授的路由祖先菜单 `300 课程管理` / `9531 课程中心`；
    保留 `9530 应用中心`（「培训成果 › 证书管理」只读下钻链路的父级载体）与 `9700/9703/9706`。
  - 新增幂等 migration `V20260930160000__unbind_course_management_from_regulator_role.sql`。
- 前后端影响：Task 6 已完成的「按渠道收敛顶部导航」逻辑不变；本次只清侧栏死入口。
  **不改前端代码、不改 API 结构、不改授权判定模型**。
- API 依赖：无新增。判据字段为 `GET /api/system/users/profile` 的 `orgType='REGULATOR'`，
  以及 `GET /api/regulation/workbench` 的 `shortcuts = ["CERTIFICATE","DATA_CENTER"]`。
- 数据影响：`us_sys_role_menu` 监管角色绑定 36 → 34 条；同步 `sts.init.schema-version`、
  `FlywayMigrationIT.INIT_SCHEMA_VERSION` 与各用例 `migrationsExecuted` 期望值。
- 验收标准：
  - 监管会话 `orgType=REGULATOR`、`orgName=监管平台`；顶部一级模块收敛为「园区监管 / 系统管理」；
  - 监管打开证书管理时侧栏归属模块不消失，侧栏一级项为 `["培训成果"]`（不再含「课程管理」）；
  - `course:category:list` 与 `course:course:list` 由 `200` 变 `403/1003`（权限真正收回，非服务层拒绝）；
  - 企业会话不受影响（仍见应用中心 / 企业工作台，`course:course:list` 仍 `200`）；migration 重复执行幂等。
- 测试方式：`FlywayMigrationIT.shouldUnbindCourseManagementFromRegulatorRoleOnUpgrade`（Docker 环境执行）；
  真实登录态已验证；前端渠道收敛以 Vite `ssrLoadModule` + 真实菜单树执行验证（见 QA §10.6）；
  pc-web 契约测试 20/20；`git diff --check`。

## Task 9：补齐监管租户角色的「标签管理 / 岗位管理」权限

- 状态：**已闭环** —— 数据/代码完成（`V20260930170000`），监管会话**真实登录态运行时证据已取得**（第五轮，见下）；
  IT 用例已补但本机无 Docker 未真实执行；Owner：fxw11。
- 输入：M5 验收衍生 —— 监管会话打开「系统管理 › 机构组织 › 用户管理」时右上角连弹两条
  `权限不足: 无此权限: org:tag:list` / `org:position:list`。
- 根因：**不是「用户管理缺权限」**，而是该页 `onMounted` **无条件**并发拉取 4 个表单下拉
  （dept `sys:dept:list` / role `sys:role:list` / tag `org:tag:list` / position `org:position:list`），
  见 `views/admin/system/org/user/index.vue` 的 `loadFormOptions`。监管角色有前两个、**一个 `org:*` 都没有**。
- 口径判定（授权漏配，按同类租户同一套补齐）：`9534`+`1511~1514`、`9535`+`1521~1524` 只授给了
  企业（role 1）与培训机构（role 6）的管理员角色，监管（role 5）是唯一缺失者；而监管的
  `sys:user:*` / `sys:role:*` / `sys:dept:*` 完整可写 ⇒ 它本就是本租户的组织管理员。
- 输出：给监管角色补齐 9534/9535 两个 C 菜单及其全部 F 按钮（**C+F 一并授出**，避免只授 F 被菜单树
  「向上补父级」递归拉出半截状态）；固化幂等 migration
  `V20260930170000__grant_tag_position_menus_to_regulator_role.sql`（按 `org_type='REGULATOR'` 解析 role_id）。
- 未采纳：① 只授 F（会把 9534/9535 拉进侧栏且留半截状态）；② 在「用户管理」(201) 下新增两个同 `perms`
  的 F 节点（同一 perms 两个 `menu_id`、弹窗两个同名勾选项，复现「看着没勾却有权限」的困惑）。
- 前后端影响：**前端零改动**；不改 API 结构。侧栏「系统管理 › 机构组织」下新增「标签管理」「岗位管理」
  两个**可用**菜单（与部门/角色管理平级，非死入口）。
- API 依赖：无新增。判据为 `org:tag:list` / `org:position:list` 在监管会话下由 `403/1003` 变 `200`。
- 数据影响：`us_sys_role_menu` 监管角色绑定 34 → 44 条；同步 `sts.init.schema-version`、
  `FlywayMigrationIT.INIT_SCHEMA_VERSION`（→ `20260930170000`）与 **8 处** `migrationsExecuted` 期望值
  （各 +1，按各 baseline 之后的迁移文件数**重算**而非照抄）。
- 验收标准：
  - 监管会话进「用户管理」不再弹 `org:tag:list` / `org:position:list` 权限不足；
  - `org:tag:list`、`org:position:list` 返回 `200`（而非 `403/1003`）；
  - 侧栏出现「标签管理」「岗位管理」，页面可正常打开；
  - 企业 / 培训机构会话授权不受影响；migration 重复执行幂等。
- 测试方式：`FlywayMigrationIT.shouldGrantTagAndPositionMenusToRegulatorRoleOnUpgrade`（需 Docker 环境执行，本机未跑）；
  共享库已手工执行同一 SQL 并逐条核对（`us_sys_role_menu` 绑定对子 34→44，`role_id` 全为 5，新增恰为目标 10 条、无丢失）；
  `git diff --check`。
- **运行时验证（2026-09-30 第五轮，真实登录态，已取得）**：靶机 = 用户 IDEA 实例（PID 17980，`0.0.0.0:20080`），
  脚本 `.workbuddy/tmp/verify-regulator-perms-20260930.mjs`（口令经环境变量注入）。结果：
  ① 登录 `http=200 code=0`；② `profile` `orgType=REGULATOR orgName=监管平台`；
  ③ 顶层模块 `["9530:应用中心","9810:园区监管","200:系统管理"]` 与 16:02 基线**完全一致**；
  ④ 菜单节点 `34→44`，新增 `[1511~1514,1521~1524,9534,9535]` 恰为目标 10 个、消失 `[]`；
  ⑤ `9534`/`9535` 为 `menuType=C`、`perms` 分别为 `org:tag:list`/`org:position:list`、各 4 子节点；
  ⑥ 用户管理页 4 个下拉（`depts/tree`、`roles`、`org/tags`、`org/positions`）**全部 `200/code=0`**；
  ⑦ 会话 `perms` 含 8 条 `org:*`（`tag`/`position` × `list/add/edit/delete`）。
  权限缓存以**精确 `EXISTS`** 复核：`sts:cache:sys:user:perms::8` = `1` 且值内已含上述 8 条（`TTL`≈1241s）⇒ 缓存为新值、无需清理
  —— 注意**不能**用 `--scan` 判「有没有」（远程 25k key 会超时返回 0，假阴性）。
- **仍未取得**：容器内 Flyway 自动执行链路（本机无 Docker）；页面渲染截图（本机无头 Chrome 不可用），
  故「不再弹两条红条」由接口级 + 菜单树级共同支撑。

## Task 10：补齐监管租户角色的「菜单管理」只读权限

- 状态：**已闭环** —— 数据/代码完成（`V20260930180000`），共享库已手工执行，
  监管会话**真实登录态运行时证据已取得**（第六轮）；IT 用例已补但本机无 Docker 未真实执行；Owner：fxw11。
- 输入：M5 验收衍生 —— 监管会话打开「系统管理 › 机构组织 › **角色管理**」时右上角弹
  `权限不足: 无此权限: sys:menu:list`。
- 根因：**不是「角色管理缺权限」**，而是该页 `onMounted` **无条件**并发拉取
  `GET /api/system/roles`（`sys:role:list`，监管有）与 `GET /api/system/menus/tree`（`sys:menu:list`，监管没有），
  见 `views/admin/system/org/role/index.vue` 的 `loadMenuTree`。
  菜单树是角色管理的**核心依赖**：新增/编辑角色弹窗用它渲染权限勾选树，`getSelectedLeafMenuIds` 用它反算叶子。
- 排查方式（本轮系统性做过，不只看这一处）：以监管会话把「监管可见的 11 个页面」的初始化依赖接口逐一实测
  （18 项），结论是**只有角色管理会弹红条**；另发现「已签发证书」页姓名静默降级（见「未一并处理」）。
- 口径判定（授权漏配，按其它租户同一套补齐）：`sys:menu:*` 只授给了企业（role 1）与培训机构（role 6），
  监管（role 5）是唯一缺失者；而监管的 `sys:user:*` / `sys:role:*` / `sys:dept:*` 完整可写 ⇒ 它本就是本租户的组织管理员。
- 输出：给监管角色补齐 `204 菜单管理`（C）与 `1031 菜单查询`（F），**刻意不授** `1032 菜单新增` / `1033 菜单编辑` /
  `1034 菜单删除` —— `us_sys_menu` 是**全局菜单定义**且 `SysMenuServiceImpl.getMenuTree` **不做租户过滤**，
  授出写权限等于让监管能改全系统菜单。固化幂等 migration
  `V20260930180000__grant_menu_read_to_regulator_role.sql`（按 `org_type='REGULATOR'` 解析 role_id）。
- 未采纳：① 后端把 `/api/system/menus/tree` 放宽为 `sys:menu:list` 或 `sys:role:list` 任一即可
  （可做到侧栏零变化，但需改后端 + 另开 change）；② 后端新增角色管理专用菜单树接口（语义最干净，改动最大）；
  ③ 仅前端收敛（`loadMenuTree` 改惰性 + 守卫）—— 只能让列表页不报错，角色配权限仍不可用。
- 前后端影响：**前端零改动**；不改 API 结构。侧栏「系统管理」下新增一个**只读**的「菜单管理」入口
  （新增/编辑/删除按钮因 `canOperate(...)` 为 `false` 而不渲染）。此副作用已在决策时确认接受。
- API 依赖：无新增。判据为 `GET /api/system/menus/tree` 在监管会话下由 `403/1003` 变 `200`。
- 数据影响：`us_sys_role_menu` 监管角色绑定 `44 → 46` 条；同步 `sts.init.schema-version`、
  `FlywayMigrationIT.INIT_SCHEMA_VERSION`（→ `20260930180000`）与 **9 处** `migrationsExecuted` 期望值
  （按各 baseline 之后的迁移文件数**重算**：13/21/11/8/7/6/6/3/2/1；两个带 `.target(...)` 的用例不受影响，仍为 1）。
- 验收标准：
  - 监管会话进「角色管理」不再弹 `sys:menu:list` 权限不足；
  - `GET /api/system/menus/tree` 返回 `200`（而非 `403/1003`）；
  - 会话 `perms` 含 `sys:menu:list`，且**不含** `sys:menu:add/edit/delete`；
  - 顶层模块与菜单树其余部分不变；企业 / 培训机构会话授权不受影响；migration 重复执行幂等。
- 测试方式：`FlywayMigrationIT.shouldGrantMenuReadOnlyPermissionToRegulatorRoleOnUpgrade`（需 Docker 环境执行，本机未跑）；
  共享库已手工执行同一 SQL 并核对（绑定对子 `44→46`，新增恰为 `{204,1031}`，`1032/1033/1034` 命中数 0）；
  编译证据：分阶段 `mvn -pl sts-service-admin -am compile` + `mvn -pl sts-service-admin test-compile`
  = `Compiling 1038 source files` + `Compiling 304 source files`，均 `BUILD SUCCESS`；
  `javap` 确认新用例已进 class；`git diff --check`。
- **运行时验证（2026-09-30 第六轮，真实登录态，已取得）**：靶机 = 本轮自起的 `20081`（验完已 kill，未占用 20080）。
  结果：① 登录 `200/code=0`；② `GET /api/system/menus/tree` 由 `403/1003` → **`200/code=0`**；
  ③ 顶层模块 `["9530:应用中心","9810:园区监管","200:系统管理"]` 与 16:02 基线一致；
  ④ 菜单节点 `34→46`，新增 `[204,1031,1511~1514,1521~1524,9534,9535]`、消失 `[]`；
  ⑤ 会话 `perms` 含 `sys:menu:list`，`sys:menu:add/edit/delete` 全 `false`；⑥ 其余页面依赖接口全 `200`、无回归。
  执行 migration 后按硬约束清了 `sts:cache:sys:user:perms::8`（`EXISTS` 1 → `DEL` → `EXISTS` 0）。
- **未一并处理**：「已签发证书」页人员姓名因缺 `common:person:list` 静默降级为编号。
  授权路线**不可行**（① 节点 `9553` 的父级是 `9531 课程中心` / `300 课程管理`，授权会把 `V20260930160000` 刚解绑的入口带回来；
  ② `PersonPickerServiceImpl` 按会话租户强过滤，监管会话查不到企业持证人）⇒ 正确修法是后端 easytrans 展示名翻译，**待决策**。
- **仍未取得**：容器内 Flyway 自动执行链路（本机无 Docker）；页面渲染截图（本机无头 Chrome 不可用）。
