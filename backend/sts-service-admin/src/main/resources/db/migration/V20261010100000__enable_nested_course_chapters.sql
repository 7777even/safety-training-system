-- 课程目录不再限制根级，祖先路径支持任意业务层级。
ALTER TABLE sts_course_chapter MODIFY COLUMN ancestors LONGTEXT NOT NULL COMMENT '祖级路径', MODIFY COLUMN parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父章节ID（0为根级）';
