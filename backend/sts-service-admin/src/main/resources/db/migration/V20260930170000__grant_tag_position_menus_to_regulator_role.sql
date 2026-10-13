-- 给监管租户角色补齐「标签管理 / 岗位管理」整套菜单与按钮权限。
--
-- 背景：用户管理页（menu_id 201）在 onMounted 阶段**无条件**并发拉取 4 个表单下拉：
--   部门树 `sys:dept:list` / 角色选项 `sys:role:list` / 标签分页 `org:tag:list` / 岗位分页 `org:position:list`
--   （frontend/pc-web/src/views/admin/system/org/user/index.vue 的 loadFormOptions）。
--   监管角色（role_id=5，org_type='REGULATOR'）只授了 sys:* 系列，**没有任何 org:*** ——
--   于是监管会话一打开用户管理就在右上角弹两条
--   `权限不足: 无此权限: org:tag:list` / `... org:position:list`（实测 403）。
--
-- 口径：监管平台是三个租户里**唯一**缺「标签管理 / 岗位管理」的管理员角色 ——
--   企业（role_id=1）与培训机构（role_id=6）都同时绑了 9534/9535 及其全部 F 子节点；
--   而监管角色的 `sys:user:*` / `sys:role:*` / `sys:dept:*` 是**完整可写**的（1001-1005 / 1011-1015 / 1021-1024），
--   侧栏「系统管理 › 机构组织」下也已有部门管理/角色管理/用户管理 ⇒ 它是本租户的组织管理员。
--   据此判定为**授权漏配**，按其它租户的同一套补齐（C 菜单 + 其全部 F 按钮）。
--
-- 为什么不把 `org:tag:list` / `org:position:list` 挂到「用户管理」菜单下当新 F 节点：
--   同一 perms code 会出现两个 menu_id，角色编辑弹窗出现两个同名勾选项，
--   并复现「按钮看着没勾却实际有权限」的困惑（同 V20260929120000 那次『监管没勾应用中心却能看到』的病灶）。
-- 为什么不只授 F（1511/1521）：
--   仍会连带拉出父菜单 —— 菜单树查询是「向下授权 + **向上补父级**」递归（判据见 RbacPermissionQueryIT），
--   授 1511 会把 9534 拉进侧栏。此处本就希望这两个菜单出现，故 C 与 F 一并授出，不产生半截状态。
--
-- 范围：只针对 org_type='REGULATOR' 的角色（当前库中即 role_id=5「监管管理员」）。
--   企业 / 培训机构租户不受影响；菜单节点本身不新建、不软删。
-- 幂等：INSERT IGNORE + 按 org_type 解析 role_id，重复执行结果不变。
--
-- 注意（硬约束）：**数据类 migration 不会失效权限缓存**。
--   共享库执行后必须清 `sts:cache:sys:user:perms::<userId>`（TTL 30 分钟），否则监管用户 30 分钟内仍报 403；
--   走服务层 PUT /api/system/roles/{id} 则自带 evictAllPerms()，无需手工清。
--
-- 回滚：删掉 role_id=5 与这 10 个 menu_id 的组合即可，见
--   .workbuddy/tmp/rollback-regulator-tag-position-menu-20260930.sql。

INSERT IGNORE INTO `us_sys_role_menu` (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM `us_sys_role` r
INNER JOIN `sts_org_organization` o
  ON o.organization_id = r.organization_id AND o.deleted = 0
INNER JOIN `us_sys_menu` m
  ON m.menu_id IN (9534, 1511, 1512, 1513, 1514, 9535, 1521, 1522, 1523, 1524) AND m.deleted = 0
WHERE r.role_code = 'admin'
  AND r.deleted = 0
  AND o.org_type = 'REGULATOR';
