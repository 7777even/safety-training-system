-- 第七批 T1：人员手工特征与条件用户组，不保存自动画像或任务名单。
CREATE TABLE sts_person_profile (
  profile_id bigint NOT NULL AUTO_INCREMENT COMMENT '特征ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  user_id bigint NOT NULL COMMENT '人员ID',
  birth_date date DEFAULT NULL COMMENT '出生日期',
  gender varchar(16) DEFAULT NULL COMMENT '性别',
  occupation varchar(1000) DEFAULT NULL COMMENT '职业',
  education varchar(1000) DEFAULT NULL COMMENT '学历',
  major varchar(1000) DEFAULT NULL COMMENT '专业',
  work_experience varchar(1000) DEFAULT NULL COMMENT '从业经历',
  specialty varchar(1000) DEFAULT NULL COMMENT '专长',
  qualification varchar(1000) DEFAULT NULL COMMENT '职业资格',
  reward_penalty_note varchar(1000) DEFAULT NULL COMMENT '人工人员奖惩说明',
  work_content varchar(1000) DEFAULT NULL COMMENT '工作内容',
  work_skills varchar(1000) DEFAULT NULL COMMENT '工作技能',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (profile_id),
  UNIQUE KEY uk_person_profile (organization_id,user_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员手工特征';

CREATE TABLE sts_person_group (
  group_id bigint NOT NULL AUTO_INCREMENT COMMENT '用户组ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  group_name varchar(100) NOT NULL COMMENT '名称',
  status tinyint NOT NULL COMMENT '0停用1启用',
  conditions_json json NOT NULL COMMENT '固定分类条件',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (group_id),
  UNIQUE KEY uk_person_group (organization_id,group_name,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='条件用户组';

-- 仅新增菜单，不自动扩权已有角色；新开户由既有 bindAdminPermissions 流程纳入。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9830,500,'人员分类与用户组','group','system/org/group/index','person:group:list','User',8,1,'C',200,0,0),
(9831,9830,'用户组维护','','','person:group:edit','',8,1,'F',200,0,0),
(9832,201,'人员特征查看','','','person:profile:list','',8,1,'F',200,0,0),
(9833,201,'人员特征维护','','','person:profile:edit','',8,1,'F',200,0,0);
