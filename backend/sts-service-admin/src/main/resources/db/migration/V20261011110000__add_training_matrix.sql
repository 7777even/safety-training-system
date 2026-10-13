-- 岗位培训矩阵及人工计划来源快照。
CREATE TABLE sts_training_matrix (
  matrix_id bigint NOT NULL AUTO_INCREMENT COMMENT '矩阵要求ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  position_id bigint NOT NULL COMMENT '岗位ID',
  position_name varchar(100) NOT NULL COMMENT '岗位名称快照',
  content_name varchar(150) NOT NULL COMMENT '培训内容',
  course_id bigint NOT NULL COMMENT '课程ID',
  course_version_id bigint NOT NULL COMMENT '不可变课程版本ID',
  course_name varchar(200) NOT NULL COMMENT '课程名称快照',
  required_hours decimal(8,2) NOT NULL COMMENT '要求学时',
  status tinyint NOT NULL COMMENT '启用状态',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY(matrix_id),
  KEY idx_matrix_position(organization_id,position_id,status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位培训矩阵';
CREATE TABLE sts_training_matrix_source (
  source_id bigint NOT NULL AUTO_INCREMENT COMMENT '来源快照ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  training_plan_id bigint NOT NULL COMMENT '计划ID',
  snapshot_json json NOT NULL COMMENT '创建时完整矩阵要求快照',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY(source_id),
  UNIQUE KEY uk_matrix_source(organization_id,training_plan_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划矩阵来源';

-- 只登记菜单，不给已有角色自动扩权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9840,800,'岗位培训矩阵','matrix','business-center/training/matrix/index','training:matrix:list','Grid',8,1,'C',9530,0,0),
(9841,9840,'矩阵维护','','','training:matrix:edit','',1,1,'F',9530,0,0);
