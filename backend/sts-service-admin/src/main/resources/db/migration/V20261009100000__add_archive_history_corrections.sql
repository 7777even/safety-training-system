-- 档案展示更正只追加，不回写业务事实，不自动绑定任何现有角色。
CREATE TABLE sts_archive_history_correction (
  correction_id bigint NOT NULL AUTO_INCREMENT COMMENT '更正主键',
  organization_id bigint NOT NULL COMMENT '原记录机构',
  user_id bigint NOT NULL COMMENT '学员主键',
  record_type varchar(32) NOT NULL COMMENT '来源类型',
  record_id bigint NOT NULL COMMENT '来源记录主键',
  version int NOT NULL COMMENT '递增档案展示版本',
  before_json json NOT NULL COMMENT '本次字段在当前展示版本的前值',
  after_json json NOT NULL COMMENT '本次字段更正后值',
  reason varchar(500) NOT NULL COMMENT '更正理由',
  operator_id bigint NOT NULL COMMENT '授权操作人',
  operator_name varchar(100) DEFAULT NULL COMMENT '操作人姓名快照',
  operated_time datetime NOT NULL COMMENT '追加时间',
  PRIMARY KEY (correction_id),
  UNIQUE KEY uk_history_version (organization_id,user_id,record_type,record_id,version),
  KEY idx_history_user (organization_id,user_id,operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='只追加的档案展示更正账本';

INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,sort_order,menu_type,module_id)
VALUES (9722,9702,'档案历史更正','','','archive:history:correct',3,'F',9530),
       (9723,9702,'档案更正留痕','','','archive:history:correction:list',4,'F',9530);
