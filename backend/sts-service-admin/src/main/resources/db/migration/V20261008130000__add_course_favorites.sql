CREATE TABLE `sts_course_favorite` (
  `course_favorite_id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏记录主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `course_id` bigint NOT NULL COMMENT '课程',
  `is_favorite` tinyint NOT NULL DEFAULT 1 COMMENT '是否收藏',
  `course_name_snapshot` varchar(255) NOT NULL COMMENT '课程名称快照',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`course_favorite_id`),
  UNIQUE KEY uk_course_favorite (organization_id,user_id,course_id,deleted),
  KEY idx_course_favorite_list (organization_id,user_id,is_favorite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本人课程收藏';
