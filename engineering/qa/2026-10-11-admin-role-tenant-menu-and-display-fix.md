# 管理员角色误持租户主数据权限 与「所属租户」裸主键显示 处置记录

日期：2026-10-11　会话对象：DEMO（示例企业）管理员　环境：开发库 `19.191.192.146:8045/sts`

## 一、结论前置

1. **管理员角色的权限配置确实有问题**：DEMO 企业管理员角色（`us_sys_role.role_id = 1`）
   误持 **租户主数据菜单** `9532 机构管理` 及其按钮 `1381 机构查询 / 1382 机构新增 / 1383 机构编辑`
   （perms `system:tenant:list / add / edit`）。机构是租户主数据本身，按设计只由 superAdmin 或监管租户维护。
2. 这 4 条权限是**三处症状的共同根因**：机构管理页 4048、标签/岗位/角色弹窗的 `OrganizationSelect`
   打 `GET /api/org/organizations` 报 4048、以及「所属租户」回退成机构主键 `2`。
3. **正确的管理员权限**：企业/培训机构管理员**不得**持有 `system:tenant:*`（9532 及三按钮）与
   `module_id = 9810` 的监管模块菜单；租户内的部门/用户/角色/标签/岗位/人员分类与用户组应完整保留。
4. 除修数据外，已补**服务端兜底**：角色保存时对非监管租户剔除这 4 个菜单，阻断「勾选『机构组织』
   目录级联勾上『机构管理』→ 保存写回」的复发通道。

## 二、根因链条（逐条有实证）

### 2.1 症状 ← 权限配置

| 观测 | 实测值 |
|---|---|
| `GET /api/system/users/profile`（DEMO/admin） | `perms` 含 `system:tenant:list / add / edit`，共 183 条 |
| `GET /api/org/organizations?pageNum=1&pageSize=10` | `HTTP 403` + `{"code":4048,"message":"仅系统管理员或监管租户可管理租户"}` |
| 角色授权用的 `GET /api/system/menus/tree` | 仍返回 `9532:机构管理 / 1381 / 1382 / 1383` |
| 库内 `us_sys_role_menu`（角色 1） | 9532 / 1381 / 1382 / 1383 四条绑定均在，`create_time = 2026-10-11 15:27:18` |

前端 `utils/tenant-context.ts#canManageTenantScope` 只认 `system:tenant:list`。
权限在 ⇒ 角色 / 标签 / 岗位三个页面的弹窗渲染 `OrganizationSelect` ⇒ 组件挂载即打机构分页接口
⇒ 403 / 4048 弹出「仅系统管理员或监管租户可管理租户」；选项加载失败后 `ElSelect` 无匹配项，
直接把 `v-model` 的机构主键 `2` 渲染出来 —— 即截图里的「所属租户 = 2」。

### 2.2 为什么会复发（关键）

`V20260930150000`（2026-10-08 已在共享库执行成功，`flyway_schema_history.success = 1`）
与 `SeedDataFillerService` 都已排除 9532 系列，但库内至今仍存在 ⇒ **绑定是在迁移之后被重新写入的**。

- 写入者是 `create_by = 2`（DEMO 管理员本人），时间 2026-10-11 15:27:18；
- 载体是 `PUT /api/system/roles/1`：`SysRoleServiceImpl.bindMenus()` 是**全删全插**，
  因此 `create_time` 只证明「今天保存过该角色」，不能证明「今天新增了这 4 条」；
- 复发通道：菜单 `500 机构组织` 与 `9532 机构管理` 同级且是它的父级目录，
  角色编辑树里**勾选「机构组织」会级联勾上 9532 及其三个按钮**，`getCheckedKeys()` 原样回传 ⇒ 一次保存即写回。

> 换言之：只做数据解绑（迁移已做过一次）不足以根治，必须有服务端兜底。

### 2.3 横向对照（判定「是漏配还是口径」）

| role_id | 租户 | 类型 | 9532 系列绑定数 |
|---|---|---|---|
| 1 | DEMO | ENTERPRISE | **4** ← 异常 |
| 5 | REGULATOR | REGULATOR | 0（走 9810 模块下的 9814 独立入口） |
| 6 | M5QA_TI_20260930 | TRAINING_INSTITUTION | 0 |

只有企业租户这一个角色持有 ⇒ 属**误配**，不是口径分歧。

## 三、落点

| # | 文件 | 改动 |
|---|---|---|
| 1 | `frontend/pc-web/src/utils/tenant-context.ts` | 新增 `currentTenantLabel(userInfo)`：优先 `orgName`，缺失才回退主键 |
| 2 | `frontend/pc-web/src/views/admin/system/org/role/index.vue` | 弹窗「所属租户」`ElText` 改渲染租户名 |
| 3 | `frontend/pc-web/src/views/admin/system/org/tag/index.vue` | 同上（「所属机构」） |
| 4 | `frontend/pc-web/src/views/admin/system/org/position/index.vue` | 同上（「所属机构」） |
| 5 | `backend/.../admin/system/service/SysRoleServiceImpl.java` | 新增 `TENANT_MASTER_DATA_MENU_IDS`；`bindMenus` 增 `organizationId` 形参，非监管租户剔除这 4 个菜单；新增 `isRegulatorOrganization()` |
| 6 | `backend/.../admin/system/service/SysRoleServiceImplTest.java` | 新增 2 项用例：非监管剔除 / 监管保留 |

**数据修正**：走服务层 `PUT /api/system/roles/1`（`bindMenus` 全删全插 + 末尾 `evictAllPerms()`，
权限缓存自动失效，无需手工清 Redis），`menuIds` 剔除 4 个 ID。
回滚凭证：`.workbuddy/tmp/rollback-role1-role-menu-20261011.sql`（变更前 229 条完整绑定）。
修正脚本（可复跑）：`.workbuddy/tmp/fix-role1-tenant-menus.mjs`。

## 四、验证判据（全部实测）

### 4.1 真实登录态（最强判据：错误码跃迁）

| 观测 | 修复前 | 修复后 |
|---|---|---|
| `GET /api/org/organizations` | `403 + 4048 仅系统管理员或监管租户可管理租户`（服务层拒绝） | **`403 + 1003 权限不足：无此权限：system:tenant:list`**（权限层拒绝，权限已真正收回） |
| `profile.perms` 中 `system:tenant:*` | `list / add / edit` | **`[]`**（perms 总数 183 → 180） |
| `profile.menus` 顶层 | 侧栏含「机构管理」 | **不含**；顶层为 `门户 / 模块首页概览 / 应用中心 / 企业工作台 / 系统管理` |
| 库内 `us_sys_role_menu` 角色 1 | 229 条，含 9532 系列 4 条 | **225 条，9532 系列 0 条** |
| 标签/岗位/角色页面其余接口 | — | `GET /api/system/roles`、`/api/system/roles/1`、`/api/system/menus/tree`、`/api/org/tags`、`/api/org/positions` 全部 `HTTP 200` |

### 4.2 自动化

- 后端单测 `SysRoleServiceImplTest`：**14 项 0 失败，BUILD SUCCESS**（含新增 2 项）。
- 前端契约测试（`system-admin-closure` / `role-menu-tree` / `pc-web-ui-standard`）：21 项 19 通过 2 失败；
  2 项失败为**既有基线失败**（报错点分别落在 `exams/exam/components/OfflineScoreDialog.vue`
  与 `exams/question/create/index.vue`，均非本次改动文件，且用例名与批 7 HEAD 基线失败清单一致）⇒ **零新增失败**。
- PC `vue-tsc --noEmit`：`EXIT=0`；改动 4 个前端文件 `eslint --max-warnings=0`：`EXIT=0`。

## 五、管理员（企业/培训机构内置）正确权限口径

**应持有**
- `200 系统管理 › 500 机构组织` 下除「机构管理」外的全部：部门 203、用户 201、角色 202、标签 9534、
  岗位 9535、人员分类与用户组 9830；
- 系统管理其余子模块（菜单 204、字典 205 等）与其按钮权限；
- 业务中心（应用中心 / 企业工作台）全部企业侧能力。

**不得持有**
- `9532 机构管理` 与 `1381 / 1382 / 1383`（`system:tenant:list / add / edit`）——租户主数据，
  后端 `OrganizationServiceImpl#requireOrgManager` 只放行 superAdmin 与监管租户；
- `module_id = 9810` 的监管模块菜单（监管租户专属，企业侧由 `SeedDataFillerService` / `bindAdminPermissions` 排除）。

**监管租户** 的「管理员」是另一套：走 9810 模块（机构管理为 9814），按 `RegulatorRolePermissions`
精确绑定，不受本次改动影响。

## 六、残余与未执行项

1. **运行中的后端实例仍是旧代码**：本次服务端兜底需**重启后端**（IDEA 重启 sts-service-admin）才生效；
   在重启前，从角色页保存一次角色仍可能把 9532 写回。
2. 未新增 Flyway 迁移：`V20260930150000` 已覆盖该数据修正，且服务端兜底才是根治手段；
   共享库 Flyway 默认不跑，已按项目惯例手工走服务层修正并留回滚脚本。
3. 未在角色授权菜单树（`GET /api/system/menus/tree`）层面隐藏 9532：该接口同时服务「菜单管理」页，
   改动会外溢；服务端兜底已可保证数据不被写回。若产品要求「勾选项里不出现机构管理」，再单独立项。
4. 前端「所属租户」在 `orgName` 缺失时仍回退主键（不致空白）；实测 profile 已稳定回填 `示例企业`。
