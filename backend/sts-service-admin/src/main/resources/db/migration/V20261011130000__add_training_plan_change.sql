-- 独立变更草稿和审批；原计划在批准前保持有效。
CREATE TABLE sts_training_plan_change (
  change_id bigint NOT NULL AUTO_INCREMENT COMMENT '变更ID',
  organization_id bigint NOT NULL COMMENT '租户',
  training_plan_id bigint NOT NULL COMMENT '生效计划',
  base_version int NOT NULL COMMENT '申请基准版本',
  submitter_user_id bigint NOT NULL COMMENT '提交人',
  approver_user_id bigint DEFAULT NULL COMMENT '指定审批人',
  change_status varchar(20) NOT NULL COMMENT '变更审批状态',
  request_json json NOT NULL COMMENT '待审批变更和冻结名单',
  before_snapshot_json json NOT NULL COMMENT '原计划快照',
  before_participants_json json NOT NULL COMMENT '原参训名单快照',
  decision_comment varchar(1000) DEFAULT NULL COMMENT '审批意见',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY(change_id),
  KEY idx_plan_change(organization_id,training_plan_id,change_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训计划变更审批';

-- 仅登记权限，不扩大既有角色授权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES (9853,705,'计划变更申请','','','training:plan:change','',23,1,'F',9530,0,0);
