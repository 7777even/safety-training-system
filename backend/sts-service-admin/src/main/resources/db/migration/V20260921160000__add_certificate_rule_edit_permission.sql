-- 为证书签发规则更新接口增加可授权的独立编辑权限，并收敛证书按钮排序。
INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`,
 `is_visible`, `menu_type`, `module_id`, `deleted`)
VALUES (8513, 805, '规则编辑', '', '', 'certificate:rule:edit', '', 9, 1, 'F', 800, 0) ON DUPLICATE KEY
UPDATE
  `parent_id` =
VALUES (`parent_id`), `menu_name` =
VALUES (`menu_name`), `path` =
VALUES (`path`), `component` =
VALUES (`component`), `perms` =
VALUES (`perms`), `icon` =
VALUES (`icon`), `sort_order` =
VALUES (`sort_order`), `is_visible` =
VALUES (`is_visible`), `menu_type` =
VALUES (`menu_type`), `module_id` =
VALUES (`module_id`), `deleted` = 0;

UPDATE `us_sys_menu`
SET `sort_order` = CASE `menu_id`
                     WHEN 8509 THEN 10
                     WHEN 8510 THEN 11
                     WHEN 8511 THEN 12
                     WHEN 8512 THEN 13
                     ELSE `sort_order`
  END
WHERE `menu_id` IN (8509, 8510, 8511, 8512)
  AND `deleted` = 0;
