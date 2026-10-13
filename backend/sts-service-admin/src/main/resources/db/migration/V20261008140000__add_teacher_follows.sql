CREATE TABLE `sts_teacher_follow` (
  `teacher_follow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关注记录主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `teacher_id` bigint NOT NULL COMMENT '已有教师用户',
  `is_followed` tinyint NOT NULL DEFAULT 1 COMMENT '是否关注',
  `teacher_name_snapshot` varchar(255) NOT NULL COMMENT '教师姓名快照',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`teacher_follow_id`),
  UNIQUE KEY uk_teacher_follow (organization_id,user_id,teacher_id,deleted),
  KEY idx_teacher_follow_list (organization_id,user_id,is_followed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本人教师关注';
