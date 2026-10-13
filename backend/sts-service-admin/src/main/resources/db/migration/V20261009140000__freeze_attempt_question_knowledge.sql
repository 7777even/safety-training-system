-- 第五批课程统计：仅新作答冻结知识点，历史关联不回填。
ALTER TABLE sts_exam_attempt_question ADD COLUMN knowledge_point_ids_snapshot_json json DEFAULT NULL COMMENT '作答冻结知识点ID数组，历史NULL未记录';
