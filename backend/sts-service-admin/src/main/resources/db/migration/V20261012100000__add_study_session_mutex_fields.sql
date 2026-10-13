-- 学习互斥：终端标识用于同一终端多账户检测；活跃索引服务互斥查询。
ALTER TABLE sts_course_study_session ADD COLUMN device_id varchar(64) NOT NULL DEFAULT '' COMMENT '终端标识（客户端生成并持久化，用于同一终端多账户互斥检测）' AFTER device_type;
ALTER TABLE sts_course_study_session ADD KEY idx_user_active (user_id, update_time);
ALTER TABLE sts_course_study_session ADD KEY idx_device_active (device_id, update_time);
