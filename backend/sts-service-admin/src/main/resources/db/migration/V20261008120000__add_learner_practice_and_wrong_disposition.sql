ALTER TABLE sts_question_user_state ADD COLUMN handled_wrong_key varchar(100) DEFAULT NULL COMMENT '已处理错题事实标识', ADD COLUMN wrong_disposition varchar(20) DEFAULT NULL COMMENT 'DELETED或MASTERED';

CREATE TABLE `sts_learner_practice_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `candidate_id` bigint DEFAULT NULL COMMENT '分配练习',
  `source_question_id` bigint DEFAULT NULL COMMENT '错题来源',
  `mode` varchar(20) NOT NULL COMMENT '练习模式',
  `title` varchar(255) DEFAULT NULL COMMENT '题源名称',
  `status` varchar(20) NOT NULL COMMENT '会话状态',
  `client_token` varchar(100) NOT NULL COMMENT '启动令牌',
  `started_time` datetime NOT NULL COMMENT '开始时间',
  `submitted_time` datetime DEFAULT NULL COMMENT '交卷时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`session_id`),
  UNIQUE KEY uk_practice_token (organization_id,user_id,client_token,deleted),
  KEY idx_practice_user (organization_id,user_id,session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人练习记录';

CREATE TABLE `sts_learner_practice_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `session_id` bigint NOT NULL COMMENT '练习会话',
  `question_id` bigint NOT NULL COMMENT '原题目',
  `question_version_id` bigint NOT NULL COMMENT '冻结版本',
  `snapshot_json` longtext NOT NULL COMMENT '含答案的服务端快照',
  `answer_json` longtext DEFAULT NULL COMMENT '本人作答',
  `answered` tinyint NOT NULL DEFAULT 0 COMMENT '是否作答',
  `evaluation_result` varchar(30) DEFAULT NULL COMMENT '判定结果',
  `sort_order` int NOT NULL COMMENT '题序',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY uk_practice_item (organization_id,session_id,sort_order,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人练习记录';
