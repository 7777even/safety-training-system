-- 将历史材料规则收敛到内容项的唯一要求学习时长字段。
UPDATE `sts_course_item` item
  LEFT JOIN `sts_course_item_material_rule` rule
    ON rule.`course_item_id` = item.`course_item_id` AND rule.`deleted` = 0
  LEFT JOIN `sts_course_setting` setting
    ON setting.`organization_id` = item.`organization_id` AND setting.`deleted` = 0
SET item.`minimum_study_seconds` = CASE
  WHEN rule.`required_seconds` > 0 THEN rule.`required_seconds`
  WHEN item.`minimum_study_seconds` > 0 THEN item.`minimum_study_seconds`
  WHEN setting.`default_document_page_seconds` > 0 THEN setting.`default_document_page_seconds`
  ELSE 15
END
WHERE item.`deleted` = 0
  AND item.`item_type` = 'ASSET';

UPDATE `sts_course_item`
SET `minimum_study_seconds` = 0
WHERE `deleted` = 0
  AND `item_type` <> 'ASSET';

-- 先解除角色关联，再逻辑删除课程设置菜单和权限点。
DELETE FROM `us_sys_role_menu`
WHERE `menu_id` IN (302, 1311, 1312);

UPDATE `us_sys_menu`
SET `deleted` = `menu_id`,
    `is_visible` = 0
WHERE `menu_id` IN (302, 1311, 1312)
  AND `deleted` = 0;

DROP TABLE `sts_course_item_material_rule`;

-- 机构课程设置能力已从页面、API 和业务规则移除；保留历史表和值，后续启用必须重新确认规格。
ALTER TABLE `sts_course_setting`
  MODIFY COLUMN `default_document_page_seconds` int NOT NULL COMMENT '【暂时隐藏】原用于：文档每页默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `default_rich_text_seconds` int NOT NULL COMMENT '【暂时隐藏】原用于：图文课件默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `default_link_live_seconds` int NOT NULL COMMENT '【暂时隐藏】原用于：链接或直播课件默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `default_media_completion_ratio` decimal (7, 4) NOT NULL COMMENT '【暂时隐藏】原用于：音视频默认完成比例；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_category_permission_enabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：课程分类权限开关；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_resume_prompt_enabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：断点续播提示开关；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_last_page_required` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：强制学习文档最后一页；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_fast_forward_globally_disabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：全局禁止拖动快进；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_screen_leave_time_paused` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：离开学习页面时暂停计时；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_transcode_notification_enabled` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：向发布人发送转码完成通知；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  COMMENT = '【暂时隐藏】原用于：机构统一课程设置；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';
