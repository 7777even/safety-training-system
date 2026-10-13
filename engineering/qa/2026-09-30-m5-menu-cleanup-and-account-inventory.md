# M5 重复监管菜单清理与三租户账号清单（2026-09-30）

配套阅读：`2026-09-30-m5-pc-regulation-acceptance.md`（本轮验收与菜单审计）。

## 一、重复监管菜单清理（已执行）

### 根因补充证据

`us_sys_menu` 的 9752～9760 九行由 **superAdmin（create_by=1）在 2026-09-29 16:57～17:02 通过菜单管理界面手工建立**，
创建时间逐分钟递增，`module_id` 为 `NULL`、角色绑定 0 条。随后 `V20260929120000` 于 2026-09-30 09:03 正式写入 9810 树，
两树路径同为 `/admin/regulation` ⇒ superAdmin 因全局权限旁路不过滤角色绑定，同时看到两个「园区监管」入口。

### 备份（执行前）

```
.workbuddy/tmp/backup-9752-menu.tsv        # 9 行（含 menu_id/parent_id/module_id/deleted/version）
.workbuddy/tmp/backup-9752-rolemenu.tsv    # 0 行（该树从未绑定角色）
```

### 落地方式

1. 增量 migration（幂等，不可改名）：
   `backend/sts-service-admin/src/main/resources/db/migration/V20260930110000__remove_legacy_duplicated_regulation_menu.sql`
   —— `DELETE` 该范围的角色绑定；`UPDATE` 按库内既有失效语义软删（`deleted = menu_id`、`is_visible = 0`），
   并以 `module_id IS NULL` 收窄作用域，避免误伤同号段的正常数据。
2. `backend/sts-service-admin-init/src/main/resources/application.yml`：`sts.init.schema-version` → `20260930110000`
   （与 V20260929120000 的处理一致；两次增量都只改菜单数据，而菜单由 `menus.json` 提供，新建库以该版本基线即可）。
3. `FlywayMigrationIT`：`INIT_SCHEMA_VERSION` → `20260930110000`；各用例 `migrationsExecuted` 期望值同步
   （7→9、15→17、5→7、2→4、1→3），并新增 `shouldRemoveLegacyDuplicatedRegulationMenuOnUpgrade`。

### 执行与复验（共享库）

```
updated_rows = 9
```

| 复验项 | 结果 |
|---|---|
| 9752～9760 九行 | `is_visible = 0`、`deleted = menu_id` |
| `us_sys_role_menu` 该范围残留 | 0 行 |
| `menu_type='D' AND deleted=0` 的路径重复 | 无（`GROUP BY path HAVING COUNT(*)>1` 空结果） |
| 顶级目录 | 门户 / 模块首页概览 / 应用中心 / 企业工作台 / **园区监管(9810)** / 系统管理 |
| `GET /api/system/menus/tree`（DEMO/admin 登录态，HTTP 200 `code=0`） | 9750～9815 区间只剩 9750、9751、9761、9800、9801、9810～9814；**9752 树不再返回** |

菜单无 Redis 缓存（`@Cacheable` 只用于 `SYS_USER` / `SYS_USER_PERMS` / `SYS_USER_ROLES`），无需清缓存。

**未验证**：`FlywayMigrationIT` 与 `AdminInitFlywayIT` 需要 Docker（Testcontainers），本机无 Docker，本轮只做了
`mvn -P integration-test test-compile` 编译通过，未实际执行用例。

## 二、三租户账号清单

`sts_org_organization` 现有 3 个启用租户：

| organization_id | org_code | 名称 | org_type |
|---|---|---|---|
| 1 | `REGULATOR` | 监管平台 | `REGULATOR` |
| 2 | `DEMO` | 示例企业 | `ENTERPRISE` |
| 3 | `M5QA_TI_20260930` | M5验收培训机构 | `TRAINING_INSTITUTION` |

### 监管平台（org 1）

| user_id | 工号 | 姓名 | 成员类型 | 角色 | 部门 |
|---|---|---|---|---|---|
| 8 | `regulatorAdmin` | M5监管验收管理员 | REGULATOR | `admin` 监管管理员（ADMIN，36 项菜单权限） | 监管机构 |

登录坐标：企业编码 `REGULATOR` + 工号 `regulatorAdmin`。**口令未知。**

### M5验收培训机构（org 3）

| user_id | 工号 | 姓名 | 成员类型 | 角色 | 部门 |
|---|---|---|---|---|---|
| 9 | `admin` | M5培训机构管理员 | INTERNAL | `admin` 管理员（ADMIN） | 无部门 |
| 10 | `m5Viewer` | M5验收查看员 | INTERNAL | `m5Viewer` M5验收查看员（ADMIN） | 培训业务部 |

登录坐标：企业编码 `M5QA_TI_20260930` + 工号 `admin` / `m5Viewer`。**口令均未知。**

### 示例企业（org 2，供对照）

`admin`(user 2)、`superAdmin`(user 1)、`operator01`、`stu_e2e_01`、`m4-learner-01`、`m4-filler-01`、`m4-expert-01`，共 7 个。

### 口令为什么给不出来

`us_sys_auth_identity.credential` 存的是 **BCrypt(SHA-256(明文))**，实测为 `$2a$10$…` 60 字符哈希，**单向不可逆**；
工作区与后端日志中也没有任何明文或可还原记录。三个账号的凭证标识与哈希长度：

```
REGULATOR:regulatorAdmin        $2a$10$ / 60
M5QA_TI_20260930:admin          $2a$10$ / 60
M5QA_TI_20260930:m5Viewer       $2a$10$ / 60
```

**因此不可能"读"出口令，只能重设。** 推荐路径见第四节。

## 三、为什么「用户管理」看不到 regulatorAdmin

**是的，用户管理是租户内的，superAdmin 也受数据范围约束。** 实测（DEMO/admin 登录态）：

```
GET /api/system/users?pageNum=1&pageSize=50
→ code=0, total=7，全部 organizationId=2
→ 不含 regulatorAdmin，不含 m5Viewer
```

代码口径：`SysUserServiceImpl#pageUsers` 不加显式机构条件，范围由租户上下文（Sa-Token 会话租户）决定；
`TenantSessionKeys.SUPER_ADMIN` 的注释写明它「只用于功能权限和平台身份判定，**不表示租户旁路**」，
`AuthApplicationServiceImpl#fillTenantSession` 也注明 superAdmin「数据范围同样绑定本次登录选择的租户」。
你这张截图是 superAdmin 以 **企业编码 `DEMO`** 登录的会话，所以只看得到 DEMO 的 7 个用户；
`regulatorAdmin` 归属 REGULATOR（organization_id=1），不在该范围。

**要看它，用企业编码 `REGULATOR` 登录即可。**

## 四、监管平台 vs 企业租户，以及"谁来开机构、谁来管机构"

| 维度 | 监管平台（REGULATOR） | 企业 / 培训机构（ENTERPRISE / TRAINING_INSTITUTION） |
|---|---|---|
| 建立方式 | **只能由初始化建立**，创建接口显式拒绝：`4048 监管平台租户只能由初始化建立` | 由监管平台或 superAdmin 通过机构开户创建 |
| 数据范围 | 无条件覆盖全部启用中的企业与培训机构（`TenantDirectory.REGULATED_TYPES`），不依赖监管关系配置 | 只能看到本租户；查询其它租户返回 `4048 仅系统管理员或监管租户可管理租户` |
| 进入他方 | 用受控只读视角（`X-Target-Tenant-Id`，仅作用于计划只读请求，退出/注销即清除） | 无跨租户能力 |
| 菜单 | 园区监管（9810：监管工作台 / 数据中心 / 学时预警 / 机构管理） | 企业工作台（9800）+ 应用中心业务菜单；**不继承监管菜单**（开户复制菜单时排除 module 950/9810） |
| 角色权限 | 监管管理员按 `RegulatorRolePermissions` 精确绑定 —— 屏蔽手段就是"不给业务写权限码" | 租户管理员按 `admin` 角色绑定本机构可用菜单 |

**新增机构找谁**：执行人必须是 **superAdmin** 或 **监管租户用户**，接口 `POST /api/org/organizations`
（页面入口：园区监管 › 机构管理 › 新增机构）。开户需企业编码、名称、类型、初始管理员工号/姓名/口令；
服务端一个事务内建租户 → 建内置 `admin` 角色并按其类型绑定菜单 → 建初始管理员用户 → 写本地账密凭证。
企业与培训机构可互转；监管平台类型不可改。

**谁管理园区下有哪些机构**：**监管平台租户的管理员**（持 `system:tenant:list/add/edit`），superAdmin 也可作为平台运维。
企业/培训机构自己无权管理租户清单 —— 这正是上一份 QA 里 `9532 系统管理 › 机构管理` 对企业租户是死菜单的原因。

## 五、需你拍板

1. **口令重置**：`regulatorAdmin` / M5QA 的 `admin`、`m5Viewer` 口令需重设才能做 M5 端到端验收。
   最省事的官方路径是：**superAdmin 登录时企业编码填 `REGULATOR`** —— 这样会话租户就是监管平台，
   既能让园区监管不再报 `4065`，也能在用户管理里看到 `regulatorAdmin` 并用「重置密码」重设。
   （该路径由代码推导：`LocalLoginStrategy` 对 superAdmin 不校验主体归属，目标租户直接决定会话租户。）
   若你不便提供 superAdmin 口令，我可以直接对 `us_sys_auth_identity` 写一个新的 BCrypt 摘要 —— **属共享库写入，需你明确授权**。
2. 非监管租户 `9532 机构管理` 死菜单的收口口径（收回 / 明确只面向 superAdmin），仍待定。
