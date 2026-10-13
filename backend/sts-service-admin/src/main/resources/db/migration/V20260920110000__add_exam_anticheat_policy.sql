ALTER TABLE `sts_exam_policy`
  ADD COLUMN `is_anticheat_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用考试防作弊事件审计（0否 1是）' AFTER `exam_id`;
