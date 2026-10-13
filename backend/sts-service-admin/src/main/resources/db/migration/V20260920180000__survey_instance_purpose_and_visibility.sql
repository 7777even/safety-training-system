-- 问卷投放增加用途和结果可见方式，支撑扫码填写最小闭环。
ALTER TABLE `sts_survey_instance`
  ADD COLUMN `purpose` varchar(32) NOT NULL DEFAULT 'TRAINING_SURVEY' COMMENT '投放用途（TRAINING_SURVEY 线下培训调查 / ORG_EVALUATION 培训机构评价）' AFTER `survey_binding_id`,
  ADD COLUMN `result_visibility` varchar(32) NOT NULL DEFAULT 'ADMIN_ONLY' COMMENT '结果可见方式（ADMIN_ONLY 仅管理员 / SUBMITTER_AFTER_SUBMIT 提交后填写人可见）' AFTER `purpose`;
