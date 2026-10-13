-- =================================================================================
-- UNISENCE (统一互联) 平台系统管理模块数据库表结构 (MySQL DDL 脚本)
--
-- DDL 全项目铁律见：harness/backend/project-features/ddl-conventions.md
-- 本文件包含系统管理域 us_sys_* 表及系统文件上传会话表。
-- =================================================================================

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 部门/组织树表 (us_sys_dept)
-- ----------------------------
CREATE TABLE `us_sys_dept`
(
  `dept_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '部门ID (主键)',
  `organization_id` bigint       NOT NULL COMMENT '所属机构ID（对应sts_org_organization）',
  `parent_id`       bigint       NOT NULL DEFAULT 0 COMMENT '父部门ID (0表示顶级部门)',
  `ancestors`       varchar(500) NOT NULL DEFAULT '' COMMENT '祖级列表 (逗号分隔，便于层级检索，如 0,1,2)',
  `dept_code`       varchar(64)  NOT NULL COMMENT '机构内部门编码',
  `dept_name`       varchar(100) NOT NULL COMMENT '部门名称',
  `dept_type`       varchar(32)  NOT NULL DEFAULT 'DEPARTMENT' COMMENT '部门类型（SCHOOL/COLLEGE/MAJOR/DEPARTMENT/CLASS/OTHER）',
  `sort_order`      int                   DEFAULT 0 COMMENT '显示顺序',
  `leader`          varchar(64)           DEFAULT NULL COMMENT '负责人',
  `phone`           varchar(11)           DEFAULT NULL COMMENT '联系电话',
  `description`     varchar(500) NOT NULL DEFAULT '' COMMENT '部门描述',
  `status`          tinyint      NOT NULL DEFAULT 1 COMMENT '部门状态 (0-禁用, 1-正常)',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`dept_id`),
  UNIQUE KEY `uk_org_dept_code_deleted` (`organization_id`, `dept_code`, `deleted`),
  KEY               `idx_parent_id` (`parent_id`),
  KEY               `idx_org_parent_status` (`organization_id`, `parent_id`, `status`, `sort_order`),
  KEY               `idx_org_dept_type` (`organization_id`, `dept_type`, `status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='部门组织树表';

-- ----------------------------
-- 2. 用户主体表 (us_sys_user)
-- ----------------------------
CREATE TABLE `us_sys_user`
(
  `user_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '用户ID (主键，数据库自增)',
  `organization_id` bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `user_code`       varchar(64) NOT NULL COMMENT '用户唯一登录账号',
  `user_name`       varchar(64) NOT NULL COMMENT '用户名称',
  `member_type`     varchar(32) NOT NULL DEFAULT 'INTERNAL' COMMENT '成员类型（INTERNAL内部/EXTERNAL外部/REGULATOR监管）',
  `email`           varchar(128)         DEFAULT NULL COMMENT '邮箱',
  `job_title`       varchar(100)         DEFAULT NULL COMMENT '职务',
  `entry_date`      date                 DEFAULT NULL COMMENT '入职日期',
  `phone`           varchar(11)          DEFAULT NULL COMMENT '手机号',
  `status`          tinyint     NOT NULL DEFAULT 1 COMMENT '账号状态 (0-禁用, 1-正常)',
  `dept_id`         bigint               DEFAULT NULL COMMENT '所属部门ID；可空表示暂未分配部门',
  `create_by`       bigint               DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint               DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_org_user_code_deleted` (`organization_id`, `user_code`, `deleted`),
  KEY               `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='用户主体表';

-- ----------------------------
-- 3. 用户认证凭证表 (us_sys_auth_identity)
-- ----------------------------
CREATE TABLE `us_sys_auth_identity`
(
  `identity_id`   bigint       NOT NULL AUTO_INCREMENT COMMENT '凭证ID (主键)',
  `user_id`       bigint       NOT NULL COMMENT '关联用户主体ID (对应us_sys_user)',
  `identity_type` varchar(32)  NOT NULL COMMENT '认证渠道类型 (如 local-本地账密, oauth2_github-GitHub三方, sms-短信码)',
  `identifier`    varchar(128) NOT NULL COMMENT '渠道唯一标识 (如 登录名、三方OpenID、手机号)',
  `credential`    varchar(255)          DEFAULT NULL COMMENT '凭证密文/密码哈希/令牌占位符',
  `create_by`     bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`       int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`identity_id`),
  UNIQUE KEY `uk_identity_type_identifier_deleted` (`identity_type`, `identifier`, `deleted`),
  KEY             `idx_user_id` (`user_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='用户认证凭证表';

-- ----------------------------
-- 4. 角色表 (us_sys_role)
-- ----------------------------
CREATE TABLE `us_sys_role`
(
  `role_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '角色ID (主键)',
  `organization_id` bigint       NOT NULL COMMENT '所属机构ID（对应sts_org_organization）',
  `role_name`       varchar(100) NOT NULL COMMENT '角色名称',
  `role_code`       varchar(100) NOT NULL COMMENT '机构内角色编码 (如 ROLE_ADMIN, ROLE_OPERATOR)',
  `role_level`      varchar(16)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色级别（ADMIN/TEACHER/STUDENT）',
  `is_builtin`      tinyint      NOT NULL DEFAULT 0 COMMENT '是否内置角色（0否 1是）',
  `status`          tinyint      NOT NULL DEFAULT 1 COMMENT '角色状态 (0-禁用, 1-正常)',
  `description`     varchar(500) NOT NULL DEFAULT '' COMMENT '角色描述',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_org_role_code_deleted` (`organization_id`, `role_code`, `deleted`),
  KEY               `idx_org_role_level` (`organization_id`, `role_level`, `status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='角色表';

-- ----------------------------
-- 5. 菜单与权限点表 (us_sys_menu)
-- ----------------------------
CREATE TABLE `us_sys_menu`
(
  `menu_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '菜单ID (主键)',
  `parent_id`   bigint       NOT NULL DEFAULT 0 COMMENT '父菜单ID (0表示顶级)',
  `menu_name`   varchar(100) NOT NULL COMMENT '菜单/按钮名称',
  `path`        varchar(255)          DEFAULT NULL COMMENT '前端路由访问路径 (菜单或目录使用)',
  `component`   varchar(255)          DEFAULT NULL COMMENT '前端组件路径 (菜单使用)',
  `perms`       varchar(128)          DEFAULT NULL COMMENT '权限字符串 (按钮级鉴权标识，如 sys:user:delete)',
  `icon`        varchar(100)          DEFAULT NULL COMMENT '菜单图标',
  `sort_order`  int                   DEFAULT 0 COMMENT '显示排序',
  `is_visible`  tinyint      NOT NULL DEFAULT 1 COMMENT '是否可见 (0-隐藏, 1-显示；控制是否在导航中展示)',
  `menu_type`   char(1)      NOT NULL COMMENT '类型 (D-模块, M-目录, C-菜单, F-按钮)',
  `module_id`   bigint                DEFAULT NULL COMMENT '所属模块ID (冗余字段，方便查询)',
  `create_by`   bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`     int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`menu_id`),
  KEY           `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='菜单与权限点表';

-- ----------------------------
-- 6. 用户角色关联表 (us_sys_user_role)
-- ----------------------------
CREATE TABLE `us_sys_user_role`
(
  `user_role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户角色关联ID (主键)',
  `user_id`      bigint NOT NULL COMMENT '用户ID',
  `role_id`      bigint NOT NULL COMMENT '角色ID',
  `create_by`    bigint   DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_role_id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色中间表 (C类·仅创建审计·物理删除：解绑=真删)';

-- ----------------------------
-- 7. 角色菜单关联表 (us_sys_role_menu)
-- ----------------------------
CREATE TABLE `us_sys_role_menu`
(
  `role_menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色菜单关联ID (主键)',
  `role_id`      bigint NOT NULL COMMENT '角色ID',
  `menu_id`      bigint NOT NULL COMMENT '菜单ID',
  `create_by`    bigint   DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_menu_id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单中间表 (C类·仅创建审计·物理删除：解绑=真删)';

-- ----------------------------
-- 8. 字典类型表 (us_sys_dict_type)
-- ----------------------------
CREATE TABLE `us_sys_dict_type`
(
  `dict_type_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '字典类型ID (主键)',
  `dict_name`    varchar(100) NOT NULL COMMENT '字典名称',
  `dict_type`    varchar(100) NOT NULL COMMENT '字典类型 (全局唯一大类，如 device_status)',
  `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '字典状态 (0-禁用, 1-正常)',
  `create_by`    bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`  datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`      int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`dict_type_id`),
  UNIQUE KEY `uk_dict_type_deleted` (`dict_type`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='字典类型表';

-- ----------------------------
-- 9. 字典数据表 (us_sys_dict_data)
-- ----------------------------
CREATE TABLE `us_sys_dict_data`
(
  `dict_data_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '字典数据ID (主键)',
  `sort_order`   int                   DEFAULT 0 COMMENT '字典排序',
  `dict_label`   varchar(100) NOT NULL COMMENT '字典标签 (前端展示文本，如 正常)',
  `dict_value`   varchar(100) NOT NULL COMMENT '字典键值 (数据库实际存放，如 1)',
  `dict_type`    varchar(100) NOT NULL COMMENT '所属字典类型 (关联us_sys_dict_type)',
  `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '数据状态 (0-禁用, 1-正常)',
  `create_by`    bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`  datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`      int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`dict_data_id`),
  UNIQUE KEY `uk_dict_type_value_deleted` (`dict_type`, `dict_value`, `deleted`),
  KEY            `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='字典数据表';

-- ----------------------------
-- 10. 全局参数设置表 (us_sys_config)
-- ----------------------------
CREATE TABLE `us_sys_config`
(
  `config_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name`  varchar(100) NOT NULL COMMENT '参数名称',
  `config_key`   varchar(100) NOT NULL COMMENT '参数键名 (全局唯一标识，如 sys.user.initPassword)',
  `config_value` varchar(500) NOT NULL COMMENT '参数键值',
  `config_type`  tinyint      NOT NULL DEFAULT 0 COMMENT '是否系统内置 (0-否, 1-是)',
  `create_by`    bigint                DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`  datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`      int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key_deleted` (`config_key`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='参数配置表';

-- ----------------------------
-- 11. 操作日志表 (us_sys_oper_log)
-- ----------------------------
CREATE TABLE `us_sys_oper_log`
(
  `oper_log_id`     bigint NOT NULL AUTO_INCREMENT COMMENT '操作日志主键 (主键)',
  `organization_id` bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `title`           varchar(50)  DEFAULT '' COMMENT '模块标题 (如 用户管理)',
  `business_type`   int          DEFAULT 0 COMMENT '业务类型 (0-其它, 1-新增, 2-修改, 3-删除, 4-授权, 5-强退)',
  `method`          varchar(255) DEFAULT '' COMMENT '执行的方法名 (类名+方法名)',
  `request_method`  varchar(10)  DEFAULT '' COMMENT '请求方式 (如 POST, PUT, DELETE)',
  `operator_code`   varchar(64)  DEFAULT '' COMMENT '操作人员账号',
  `oper_ip`         varchar(128) DEFAULT '' COMMENT '操作IP地址',
  `oper_url`        varchar(255) DEFAULT '' COMMENT '请求URL',
  `oper_param`      text COMMENT '请求参数 (JSON字符串，规则脚本等核心数据必须完整保存)',
  `json_result`     text COMMENT '响应结果 (返回JSON)',
  `status`          tinyint      DEFAULT 1 COMMENT '操作状态 (0-异常, 1-正常)',
  `error_msg`       text COMMENT '错误消息 (异常信息记录)',
  `create_by`       bigint       DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`oper_log_id`),
  KEY               `idx_organization_id` (`organization_id`),
  KEY               `idx_operator_code` (`operator_code`),
  KEY               `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';

-- ----------------------------
-- 12. 登录日志表 (us_sys_login_log)
-- ----------------------------
CREATE TABLE `us_sys_login_log`
(
  `login_log_id`    bigint NOT NULL AUTO_INCREMENT COMMENT '登录日志主键 (主键)',
  `organization_id` bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `user_code`       varchar(64)  DEFAULT '' COMMENT '登录账号',
  `ipaddr`          varchar(128) DEFAULT '' COMMENT '登录IP地址',
  `login_location`  varchar(255) DEFAULT '' COMMENT '登录地点',
  `browser`         varchar(64)  DEFAULT '' COMMENT '浏览器类型',
  `os`              varchar(64)  DEFAULT '' COMMENT '操作系统',
  `status`          tinyint      DEFAULT 1 COMMENT '登录状态 (0-失败, 1-成功)',
  `msg`             varchar(255) DEFAULT '' COMMENT '提示消息 (如：密码错误)',
  `login_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_by`       bigint       DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`login_log_id`),
  KEY               `idx_organization_id` (`organization_id`),
  KEY               `idx_user_code` (`user_code`),
  KEY               `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统登录日志表';

-- ----------------------------
-- 13. 文件信息存储表 (us_sys_file_info)
-- ----------------------------
CREATE TABLE `us_sys_file_info`
(
  `file_info_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '文件信息记录ID (主键)',
  `organization_id` bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `file_name`       varchar(255) NOT NULL COMMENT '文件原始名称',
  `bucket_name`     varchar(100) NOT NULL COMMENT 'MinIO 存储桶名称',
  `object_name`     varchar(255) NOT NULL COMMENT 'MinIO 存储唯一对象名 (UUID/随机串+原后缀)',
  `file_size`       bigint       NOT NULL COMMENT '文件大小 (Bytes)',
  `file_suffix`     varchar(10)           DEFAULT NULL COMMENT '文件后缀名 (如 xlsx, png)',
  `file_url`        varchar(500) NOT NULL COMMENT '只读预览临时外链或持久外链 URL',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建者用户ID (即上传者ID)',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新者用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0-未删除, 非0=已删除，值为主键ID)',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`file_info_id`),
  KEY               `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_object_name_deleted` (`object_name`, `deleted`),
  KEY               `idx_file_name` (`file_name`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='文件存储信息表';

-- ----------------------------
-- 14. 文件分片上传会话表 (sts_file_multipart_upload)
-- ----------------------------
CREATE TABLE `sts_file_multipart_upload`
(
  `multipart_upload_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '分片上传会话主键',
  `upload_id`           varchar(36)  NOT NULL COMMENT '对外不可猜测会话ID',
  `organization_id`     bigint       NOT NULL COMMENT '所属机构ID',
  `user_id`             bigint       NOT NULL COMMENT '发起用户ID',
  `provider_upload_id`  varchar(255) NOT NULL COMMENT 'MinIO/S3 multipart uploadId',
  `bucket_name`         varchar(128) NOT NULL COMMENT '私有存储桶',
  `object_name`         varchar(512) NOT NULL COMMENT '服务端生成对象键',
  `folder`              varchar(64)  NOT NULL COMMENT '业务目录标识',
  `original_file_name`  varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type`        varchar(128) NOT NULL COMMENT '文件 MIME 类型',
  `file_size`           bigint       NOT NULL COMMENT '声明文件大小（字节）',
  `checksum`            char(64)     NOT NULL COMMENT '文件 SHA-256 小写十六进制',
  `part_size`           bigint       NOT NULL COMMENT '建议分片大小（字节）',
  `upload_status`       varchar(16)  NOT NULL COMMENT '状态（UPLOADING/COMPLETED/ABORTED/EXPIRED）',
  `file_info_id`        bigint                DEFAULT NULL COMMENT '完成后文件信息ID',
  `expires_time`        datetime     NOT NULL COMMENT '会话过期时间',
  `completed_time`      datetime              DEFAULT NULL COMMENT '完成时间',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`multipart_upload_id`),
  UNIQUE KEY `uk_upload_id_deleted` (`upload_id`, `deleted`),
  KEY                   `idx_owner_resume` (`organization_id`, `user_id`, `checksum`, `file_size`, `upload_status`),
  KEY                   `idx_status_expire` (`upload_status`, `expires_time`),
  KEY                   `idx_file_info` (`file_info_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci COMMENT='文件分片上传会话表';

SET
FOREIGN_KEY_CHECKS = 1;

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
-- 档案更正权限点仅定义，不自动授权角色。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,sort_order,menu_type,module_id)
VALUES (9722,9702,'档案历史更正','','','archive:history:correct',3,'F',9530),
       (9723,9702,'档案更正留痕','','','archive:history:correction:list',4,'F',9530);

-- 仅新增菜单，不自动扩权已有角色；新开户由既有 bindAdminPermissions 流程纳入。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9830,500,'人员分类与用户组','group','system/org/group/index','person:group:list','User',8,1,'C',200,0,0),
(9831,9830,'用户组维护','','','person:group:edit','',8,1,'F',200,0,0),
(9832,201,'人员特征查看','','','person:profile:list','',8,1,'F',200,0,0),
(9833,201,'人员特征维护','','','person:profile:edit','',8,1,'F',200,0,0);

-- 只登记菜单，不给已有角色自动扩权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9840,800,'岗位培训矩阵','matrix','business-center/training/matrix/index','training:matrix:list','Grid',8,1,'C',9530,0,0),
(9841,9840,'矩阵维护','','','training:matrix:edit','',1,1,'F',9530,0,0);


-- 仅登记权限，不自动给已有角色扩权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES
(9850,705,'计划审批','','','training:plan:approve','',20,1,'F',9530,0,0),
(9851,800,'年度培训计划','annual-plan','business-center/training/annual-plan/index','training:annual:list','Document',9,1,'C',9530,0,0),
(9852,9851,'年度文件维护','','','training:annual:edit','',22,1,'F',9530,0,0);


-- 仅登记权限，不扩大既有角色授权。
INSERT INTO us_sys_menu(menu_id,parent_id,menu_name,path,component,perms,icon,sort_order,is_visible,menu_type,module_id,deleted,version) VALUES (9853,705,'计划变更申请','','','training:plan:change','',23,1,'F',9530,0,0);
