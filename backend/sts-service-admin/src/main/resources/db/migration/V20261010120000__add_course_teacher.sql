-- 主讲教师复用已有教师用户；重点内容沿用syllabus。
ALTER TABLE sts_course_course ADD COLUMN teacher_user_id BIGINT DEFAULT NULL COMMENT '主讲教师用户ID';
