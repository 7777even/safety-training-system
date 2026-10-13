CREATE TABLE sts_common_message (
  message_id bigint NOT NULL AUTO_INCREMENT COMMENT '消息主键',
  organization_id bigint NOT NULL COMMENT '所属机构',
  event_key varchar(255) NOT NULL COMMENT '业务事件唯一键',
  kind varchar(32) NOT NULL COMMENT '业务引用类型',
  title varchar(255) NOT NULL COMMENT '消息标题',
  content varchar(1000) NOT NULL COMMENT '提醒正文',
  resource_id bigint NOT NULL COMMENT '业务主引用',
  parent_id bigint DEFAULT NULL COMMENT '父业务引用',
  item_id bigint DEFAULT NULL COMMENT '课程测验内容项',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (message_id),
  UNIQUE KEY uk_message_event (organization_id,event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可覆盖的站内消息';

CREATE TABLE sts_common_message_recipient (
  recipient_id bigint NOT NULL AUTO_INCREMENT COMMENT '投递主键',
  organization_id bigint NOT NULL COMMENT '投递机构',
  message_id bigint NOT NULL COMMENT '消息主键',
  user_id bigint NOT NULL COMMENT '实际接收学员',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
  PRIMARY KEY (recipient_id),
  UNIQUE KEY uk_message_recipient (organization_id,message_id,user_id),
  KEY idx_message_user (organization_id,user_id,recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息接收人';
