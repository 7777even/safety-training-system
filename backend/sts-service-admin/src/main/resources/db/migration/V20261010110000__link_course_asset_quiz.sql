-- 课件复用同课程已有QUIZ，不新增素材配题能力。
ALTER TABLE sts_course_item ADD COLUMN linked_quiz_item_id BIGINT DEFAULT NULL COMMENT '课件关联同课程测验ID';
