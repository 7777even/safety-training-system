-- 年度文件、冻结关联及历次首次审批。
CREATE TABLE sts_training_plan_approval (
  approval_id bigint NOT NULL AUTO_INCREMENT COMMENT '审批记录ID',
  organization_id bigint NOT NULL COMMENT '租户',
  training_plan_id bigint NOT NULL COMMENT '计划',
  plan_version int NOT NULL COMMENT '计划版本',
  submitter_user_id bigint NOT NULL COMMENT '提交人',
  approver_user_id bigint NOT NULL COMMENT '指定审批人',
  approval_status varchar(20) NOT NULL COMMENT '审批状态',
  decision_comment varchar(1000) DEFAULT NULL COMMENT '审批意见',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY(approval_id),
  KEY idx_plan_approval(organization_id,training_plan_id,approval_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sts_training_plan_approval';
CREATE TABLE sts_training_annual_plan (
  annual_plan_id bigint NOT NULL AUTO_INCREMENT COMMENT '年度计划ID',
  organization_id bigint NOT NULL COMMENT '租户',
  plan_year int NOT NULL COMMENT '年度',
  plan_name varchar(150) NOT NULL COMMENT '名称',
  file_info_id bigint NOT NULL COMMENT '文件',
  file_name varchar(255) NOT NULL COMMENT '文件名',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY(annual_plan_id),
  KEY idx_annual_year(organization_id,plan_year,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sts_training_annual_plan';
CREATE TABLE sts_training_annual_plan_link (
  link_id bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  organization_id bigint NOT NULL COMMENT '租户',
  training_plan_id bigint NOT NULL COMMENT '计划',
  annual_plan_id bigint DEFAULT NULL COMMENT '年度计划',
  plan_year int DEFAULT NULL COMMENT '年度快照',
  plan_name varchar(150) DEFAULT NULL COMMENT '名称快照',
  file_info_id bigint DEFAULT NULL COMMENT '文件快照',
  file_name varchar(255) DEFAULT NULL COMMENT '文件名快照',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY(link_id),
  UNIQUE KEY uk_annual_link(organization_id,training_plan_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sts_training_annual_plan_link';

-- 仅登记权限，不自动给已有角色扩权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9850,705,'计划审批','','','training:plan:approve','',20,1,'F',9530,0,0),
(9851,800,'年度培训计划','annual-plan','business-center/training/annual-plan/index','training:annual:list','Document',9,1,'C',9530,0,0),
(9852,9851,'年度文件维护','','','training:annual:edit','',22,1,'F',9530,0,0);
