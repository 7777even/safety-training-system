-- 清理手工预览遗留的重复「园区监管」目录（menu_id 9752~9760）。
-- 背景：该子树由 superAdmin 在 2026-09-29 经菜单管理界面手工建立（module_id 为 NULL、未绑定任何角色），
-- 与 V20260929120000 正式写入的 M5 菜单树 9810 路径重复（同为 /admin/regulation）。
-- 后果：superAdmin 因全局权限旁路不过滤角色绑定，会同时看到两个「园区监管」入口。
-- 处置：按库内既有失效语义软删（deleted = 行自身 menu_id、is_visible = 0），并清掉可能存在的角色绑定。
-- 幂等：目标行不存在时为空操作；重复执行不改变结果。

DELETE
FROM `us_sys_role_menu`
WHERE `menu_id` BETWEEN 9752 AND 9760;

UPDATE `us_sys_menu`
SET `deleted`    = `menu_id`,
    `is_visible` = 0
WHERE `menu_id` BETWEEN 9752 AND 9760
  AND `module_id` IS NULL
  AND `deleted` = 0;
