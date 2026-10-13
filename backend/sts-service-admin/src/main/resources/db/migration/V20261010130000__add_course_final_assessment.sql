-- 结业测评复用QUIZ；旧报名认可学时为空沿用累计秒数。
ALTER TABLE sts_course_item ADD COLUMN is_final_assessment TINYINT NOT NULL DEFAULT 0 COMMENT '结业测评标记',
 ADD COLUMN pass_score DECIMAL(10,2) DEFAULT NULL COMMENT '结业测评及格线覆盖值';
ALTER TABLE sts_course_enrollment ADD COLUMN recognized_study_seconds INT DEFAULT NULL COMMENT '合格认可学习秒数，历史为空';
