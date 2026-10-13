-- M4 超范围表/列按中山50最小闭环标注暂时隐藏或内部保留。不删表、列、索引和历史数据。

ALTER TABLE `sts_evaluation_calculation_run`
  COMMENT = '【暂时隐藏】原用于：综合成绩计算批次；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_evaluation_calculation_source`
  COMMENT = '【暂时隐藏】原用于：综合成绩计算来源追溯；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_archive_export_job`
  COMMENT = '【暂时隐藏】原用于：档案异步导出任务中心；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_certificate_revocation`
  COMMENT = '【暂时隐藏】原用于：证书撤销记录；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_target_binding`
  COMMENT = '【暂时隐藏】原用于：问卷高级业务对象绑定；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_answer_asset`
  COMMENT = '【暂时隐藏】原用于：问卷附件题答案素材；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_sub_item`
  COMMENT = '【暂时隐藏】原用于：问卷矩阵/子项题；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_version`
  COMMENT = '【内部保留】用于：投放时冻结问卷内容快照；不提供独立页面或对外操作';

ALTER TABLE `sts_evaluation_form_version`
  COMMENT = '【内部保留】用于：评分表和查课表启用时冻结表单内容；不提供独立页面或对外操作';

ALTER TABLE `sts_archive_version`
  COMMENT = '【内部保留】用于：更新档案时生成当前快照；不提供独立页面或对外操作';

ALTER TABLE `sts_certificate_template_version`
  COMMENT = '【内部保留】用于：签发时冻结模板内容；不提供独立页面或对外操作';

ALTER TABLE `sts_training_participant`
  MODIFY COLUMN `official_calculation_run_id` bigint DEFAULT NULL COMMENT '【暂时隐藏】原用于：当前正式综合成绩计算批次ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `composite_score` decimal (10, 2) DEFAULT NULL COMMENT '【内部保留】用于：参训人当前综合分，作为页面唯一读取口径；不提供独立页面或对外操作';

ALTER TABLE `sts_archive_version`
  MODIFY COLUMN `snapshot_hash` varchar (128) NOT NULL COMMENT '【内部保留】用于：档案快照一致性校验；不提供独立页面或对外操作';

ALTER TABLE `sts_survey_version`
  MODIFY COLUMN `snapshot_hash` varchar (128) NOT NULL COMMENT '【内部保留】用于：问卷投放快照一致性校验；不提供独立页面或对外操作',
  MODIFY COLUMN `content_snapshot_json` json NOT NULL COMMENT '【内部保留】用于：投放时冻结题目和选项；不提供独立页面或对外操作';

ALTER TABLE `sts_survey_survey`
  MODIFY COLUMN `is_anonymous` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：问卷匿名展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `current_version_id` bigint DEFAULT NULL COMMENT '【内部保留】用于：当前投放快照版本ID；不提供独立页面或对外操作';

ALTER TABLE `sts_survey_instance`
  MODIFY COLUMN `survey_binding_id` bigint DEFAULT NULL COMMENT '【暂时隐藏】原用于：问卷业务对象绑定ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_anonymous_snapshot` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：投放匿名快照；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `survey_version_id` bigint NOT NULL COMMENT '【内部保留】用于：本次投放固定问卷快照；不提供独立页面或对外操作';

ALTER TABLE `sts_survey_question`
  MODIFY COLUMN `is_other_allowed` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：允许其他选项；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `is_description_allowed` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：允许补充描述；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `data_type` varchar (16) DEFAULT NULL COMMENT '【暂时隐藏】原用于：填空数据类型；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `max_star` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：评分题最高星级；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `max_upload_count` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：附件题最大上传数量；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_answer`
  MODIFY COLUMN `survey_sub_item_id` bigint DEFAULT NULL COMMENT '【暂时隐藏】原用于：矩阵/子项答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `numeric_value` decimal (10, 2) DEFAULT NULL COMMENT '【暂时隐藏】原用于：数值答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `date_value` date DEFAULT NULL COMMENT '【暂时隐藏】原用于：日期答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `time_value` time DEFAULT NULL COMMENT '【暂时隐藏】原用于：时间答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `other_text` varchar (1000) DEFAULT NULL COMMENT '【暂时隐藏】原用于：其他选项文本；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `description_text` varchar (2000) DEFAULT NULL COMMENT '【暂时隐藏】原用于：补充描述；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `sort_value` int DEFAULT NULL COMMENT '【暂时隐藏】原用于：排序题答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_survey_response`
  MODIFY COLUMN `is_anonymous` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：答卷匿名展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_certificate_certificate`
  MODIFY COLUMN `valid_from` date DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书有效开始日期；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `valid_to` date DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书有效结束日期；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `revoked_time` datetime DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书撤销时间；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `snapshot_hash` varchar (128) NOT NULL COMMENT '【内部保留】用于：已签发证书内容一致性校验；不提供独立页面或对外操作',
  MODIFY COLUMN `content_snapshot_json` json NOT NULL COMMENT '【内部保留】用于：签发时冻结模板内容；不提供独立页面或对外操作',
  MODIFY COLUMN `template_version_id` bigint NOT NULL COMMENT '【内部保留】用于：签发时冻结的模板版本；不提供独立页面或对外操作';

ALTER TABLE `sts_certificate_issue_rule`
  MODIFY COLUMN `condition_value` decimal (10, 2) DEFAULT NULL COMMENT '【暂时隐藏】原用于：复杂合格条件值；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `collection_instruction` longtext DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书领取说明；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `valid_from` datetime DEFAULT NULL COMMENT '【暂时隐藏】原用于：规则有效开始；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `valid_to` datetime DEFAULT NULL COMMENT '【暂时隐藏】原用于：规则有效结束；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_evaluation_record`
  MODIFY COLUMN `target_type` varchar (24) NOT NULL COMMENT '目标对象类型（当前专家查课使用 TRAINING_PLAN_CONTENT；PARTICIPANT/COURSE/TRAINING_PLAN/ORGANIZATION 为【暂时隐藏】原通用评价目标）';

ALTER TABLE `sts_evaluation_record_item`
  MODIFY COLUMN `calculation_source_json` json DEFAULT NULL COMMENT '【暂时隐藏】原用于：评价项计算来源快照；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change';

UPDATE `us_sys_menu`
SET `deleted`    = `menu_id`,
    `is_visible` = 0
WHERE `deleted` = 0
  AND (
  `perms` IN (
              'evaluation:calculation:calculate',
              'evaluation:calculation:list',
              'evaluation:calculation:confirm',
              'archive:export:create',
              'archive:export:list',
              'certificate:cert:issue',
              'certificate:cert:revoke',
              'certificate:cert:reissue',
              'survey:survey:publish'
    )
    OR (`menu_type` = 'C' AND `path` IN ('evaluation-record', 'calculation'))
  );
