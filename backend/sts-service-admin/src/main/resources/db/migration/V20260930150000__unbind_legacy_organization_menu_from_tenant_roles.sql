-- 解绑企业与培训机构租户管理员角色上的「机构管理」菜单（menu_id 9532 及其按钮权限 1381/1382/1383）。
--
-- 背景：机构（sts_org_organization）是租户主数据本身，只应由 superAdmin 或监管租户维护
--   （OrganizationServiceImpl#requireOrgManager 只放行这两类主体）。
--   「机构管理」挂在「200 系统管理」模块下，与部门/角色/标签/岗位一并被授予了 org2（企业）、org3（培训机构）
--   的 admin 角色，导致这两个租户的管理员侧边栏出现入口，点击后必然 403 / 4048「仅系统管理员或监管租户可管理租户」。
--   其余同批授出的菜单（用户/角色/部门/标签/岗位）都是租户内的组织单元，按会话租户过滤后可正常使用，故只收本菜单。
-- 范围：仅解绑非监管租户角色的绑定；监管租户自身走 9810 模块下的 9814「机构管理」（角色 role5 独立绑定），不受影响；
--   superAdmin 走 StpInterfaceImpl 的 "*" 权限短路，不依赖角色绑定，也不受影响。
-- 影响：企业侧 system:tenant:list/add/edit 权限随之关闭，canManageTenantScope 回退为会话租户
--   （与 utils/tenant-context.ts 注释「只有拥有租户管理权限的 superAdmin 才允许跨租户选择组织主数据」一致）。
-- 说明：菜单节点 9532 本身**不软删** —— superAdmin 仍需通过「系统管理 › 机构组织 › 机构管理」维护租户主数据。
-- 幂等：目标绑定不存在时为空操作；重复执行不改变结果。
--
-- 回滚：.workbuddy/tmp/rollback-9532-role-menu-20260930.sql（2026-09-30 共享库执行前导出）。

DELETE rm
FROM `us_sys_role_menu` rm
INNER JOIN `us_sys_role` r ON r.role_id = rm.role_id
WHERE rm.menu_id IN (9532, 1381, 1382, 1383)
  AND NOT EXISTS (SELECT 1
                  FROM `sts_org_organization` o
                  WHERE o.organization_id = r.organization_id
                    AND o.org_type = 'REGULATOR');
