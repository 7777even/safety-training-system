-- 给监管租户角色补齐「菜单管理」的**只读**权限，使角色管理页能读取菜单树。
--
-- 背景：角色管理页（menu_id 202）在 onMounted 阶段**无条件**调用
--   GET /api/system/menus/tree 拉取菜单树（frontend/pc-web/src/views/admin/system/org/role/index.vue 的 loadMenuTree），
--   用于渲染「新增 / 编辑角色」弹窗里的权限勾选树，并用 getSelectedLeafMenuIds 反算叶子 ——
--   它是角色管理的**核心依赖**，不是可选装饰（不打开弹窗也会拉）。
--   该接口受 `sys:menu:list` 保护（SysMenuController#getMenuTree），
--   而监管角色（role_id=5，org_type='REGULATOR'）没有任何 `sys:menu:*`
--   ⇒ 监管会话一进角色管理就弹 `权限不足: 无此权限：sys:menu:list`（实测 403 / code=1003）。
--
-- 口径：监管平台是三个租户里**唯一**缺「菜单管理」读权限的管理员角色 ——
--   企业（role_id=1）与培训机构（role_id=6）都绑了 204 与 1031-1034；
--   而监管角色的 `sys:user:*` / `sys:role:*` / `sys:dept:*` 是完整可写的（1001-1005 / 1011-1015 / 1021-1024），
--   侧栏「系统管理 › 机构组织」下也已有部门管理 / 角色管理 / 用户管理 ⇒ 它是本租户的组织管理员。
--   据此判定为**授权漏配**，按其它租户的同一套补齐。
--
-- 只授「读」（204 菜单管理 + 1031 菜单查询），**刻意不授** 1032 菜单新增 / 1033 菜单编辑 / 1034 菜单删除：
--   `us_sys_menu` 是**全局菜单定义**（无 organization_id 列），`SysMenuServiceImpl#getMenuTree` 也不做租户过滤；
--   一旦授出写权限，监管即可修改 / 删除全系统的菜单定义。监管侧只需要「读菜单树给角色配权限」，故按最小权限只给读。
--
-- 已知且已确认接受的副作用：菜单树查询是「向下授权 + **向上补父级**」递归（判据见 RbacPermissionQueryIT），
--   授 204 后监管侧栏「系统管理」下会新增一个**只读**的「菜单管理」入口
--   （页面上的新增 / 编辑 / 删除按钮因 canOperate('sys:menu:add'|'edit'|'delete') 均为 false 而不渲染）。
--   替代口径（后端把 /system/menus/tree 放宽为 `sys:menu:list` 或 `sys:role:list` 任一即可，
--   或新增角色管理专用的菜单树接口）可做到侧栏零变化，但需改后端并另开 change；本轮按既有「补授权」范式落地。
--
-- 未一并处理：证书页（已签发证书）的人员姓名依赖 `common:person:list`（节点 9553，父级 9531 课程中心）。
--   该条**不走授权**，两个理由：
--   ① 授 9553 会被向上补父级带出 9531 课程中心（C 型、有页面）与 300 课程管理，
--      与 V20260930160000「监管课程管理菜单解绑」的决定直接冲突；
--   ② PersonPickerServiceImpl#pagePersons 用 `TenantContext.requireTenantId()` 强过滤，
--      监管会话只返回本租户（org1）人员，查不到企业持证人 ⇒ 即便授权也修不好。
--   正确修法是后端展示名翻译（easytrans），另行评估。
--
-- 范围：只针对 org_type='REGULATOR' 的角色（当前库中即 role_id=5「监管管理员」）。
--   企业 / 培训机构租户不受影响；菜单节点本身不新建、不软删。
-- 幂等：INSERT IGNORE + 按 org_type 解析 role_id，重复执行结果不变。
--
-- 注意（硬约束）：**数据类 migration 不会失效权限缓存**。
--   共享库执行后须清 `sts:cache:sys:user:perms::<userId>`（TTL 30 分钟），否则监管用户 30 分钟内仍报 403；
--   走服务层 PUT /api/system/roles/{id} 则自带 evictAllPerms()，无需手工清。
--
-- 回滚：删掉 role_id=5 与 (204, 1031) 的组合即可，见
--   .workbuddy/tmp/rollback-regulator-menu-read-20260930.sql。

INSERT IGNORE INTO `us_sys_role_menu` (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM `us_sys_role` r
INNER JOIN `sts_org_organization` o
  ON o.organization_id = r.organization_id AND o.deleted = 0
INNER JOIN `us_sys_menu` m
  ON m.menu_id IN (204, 1031) AND m.deleted = 0
WHERE r.role_code = 'admin'
  AND r.deleted = 0
  AND o.org_type = 'REGULATOR';
