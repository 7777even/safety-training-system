-- 解绑监管租户角色上的「课程管理」路由祖先菜单（menu_id 300 课程管理、9531 课程中心）。
--
-- 背景：监管工作台原本有一条「课程制作」只读快捷入口，指向 `/admin/business-center/course/center`。
--   为让该入口能解析出父级模块与路由前缀，V20260929120000 给监管角色批量补授了 9530/300/9531/9700/9703/9706
--   这 6 个菜单。其中 9700/9703/9706 是保留的「培训成果 › 证书管理 › 已签发证书」只读下钻链路，
--   而 300 课程管理 / 9531 课程中心只为凑路由祖先，没有任何监管侧可见能力：
--     · `course:category:list`（9531 下属页面课程中心依赖的分类树）从未授予监管角色 —— 实测 403/1003；
--     · 页面本身是课程制作台，属企业侧业务，不是监管只读范围。
--   于是侧边栏出现了「应用中心 › 课程管理 › 课程中心」这一条点击必然 403 的死入口。
--   该快捷入口已在本轮从 RegulationWorkbenchService.shortcuts() 摘除（保留 CERTIFICATE / DATA_CENTER），
--   故这两个为其补授的祖先菜单也再无存在理由。
--
-- 范围：只解绑监管租户（org_type = 'REGULATOR'）角色的这两条绑定；企业/培训机构租户的课程管理授权不受影响。
--   superAdmin 走 StpInterfaceImpl 的 "*" 权限短路，不依赖角色绑定，也不受影响。
-- 说明：菜单节点 300 / 9531 本身**不软删** —— 企业侧后台仍需「应用中心 › 课程管理」这条正式入口。
-- 约束：解绑后不会被 F 子权限「拉回树里」—— 监管角色并未绑定 300 子树下的任何 F 权限
--   （9531 与 300 是它在该子树里的全部绑定），菜单树的「向上补父级」递归因此不会重建 300。
-- 幂等：目标绑定不存在时为空操作；重复执行不改变结果。
--
-- 回滚：重新插入 `us_sys_role_menu (role_id, menu_id)` 的 (5, 300)、(5, 9531) 两行即可恢复原状；
--   执行前快照见 .workbuddy/tmp/rollback-regulator-role-menu-20260930.sql。

DELETE rm
FROM `us_sys_role_menu` rm
INNER JOIN `us_sys_role` r ON r.role_id = rm.role_id
INNER JOIN `sts_org_organization` o ON o.organization_id = r.organization_id
WHERE rm.menu_id IN (300, 9531)
  AND o.org_type = 'REGULATOR';
