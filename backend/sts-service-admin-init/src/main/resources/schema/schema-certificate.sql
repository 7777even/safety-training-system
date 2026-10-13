-- Integration-test schema: certificate
SET NAMES utf8mb4;

CREATE TABLE `sts_certificate_certificate`
(
  `certificate_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '证书ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `certificate_no`          varchar(128) NOT NULL COMMENT '证书序号',
  `certificate_rule_id`     bigint                DEFAULT NULL COMMENT '证书规则ID',
  `certificate_template_id` bigint       NOT NULL COMMENT '证书模板ID',
  `template_version_id`   bigint       NOT NULL COMMENT '【内部保留】用于：签发时冻结的模板版本；不提供独立页面或对外操作',
  `holder_user_id`          bigint       NOT NULL COMMENT '证书持有人用户ID',
  `source_type`             varchar(16)  NOT NULL COMMENT '来源类型（COURSE/EXAM/TRAINING）',
  `source_id`               bigint       NOT NULL COMMENT '来源对象ID（COURSE为开课ID，EXAM为考试ID，TRAINING为培训计划ID；复训经新开课/新计划自然签发新证书）',
  `source_result_id`        bigint                DEFAULT NULL COMMENT '来源结果记录ID（COURSE为课程报名ID，EXAM为考试人员ID，TRAINING为参训人员ID）',
  `issued_time`             datetime     NOT NULL COMMENT '签发时间',
  `valid_from`            date     DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书有效开始日期；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `valid_to`              date     DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书有效结束日期；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `certificate_file_id`     bigint       NOT NULL COMMENT '证书文件ID',
  `content_snapshot_json` json         NOT NULL COMMENT '【内部保留】用于：签发时冻结模板内容；不提供独立页面或对外操作',
  `snapshot_hash`         varchar(128) NOT NULL COMMENT '【内部保留】用于：已签发证书内容一致性校验；不提供独立页面或对外操作',
  `certificate_status`      varchar(16)  NOT NULL COMMENT '证书状态（ISSUED/REVOKED/EXPIRED）',
  `revoked_time`          datetime DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书撤销时间；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_id`),
  UNIQUE KEY `uk_certificate_no_deleted` (`certificate_no`, `deleted`),
  UNIQUE KEY `uk_holder_source_deleted` (`holder_user_id`, `source_type`, `source_id`, `certificate_template_id`, `deleted`),
  KEY                       `idx_holder_status` (`holder_user_id`, `certificate_status`),
  KEY                       `idx_template_version` (`template_version_id`),
  KEY                       `idx_certificate_rule` (`certificate_rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书表';

CREATE TABLE `sts_certificate_issue_rule`
(
  `certificate_rule_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '证书规则ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `rule_name`               varchar(100) NOT NULL COMMENT '规则名称',
  `certificate_template_id` bigint       NOT NULL COMMENT '证书模板ID',
  `source_type`             varchar(16)  NOT NULL COMMENT '来源类型（COURSE/EXAM/TRAINING）',
  `source_id`               bigint       NOT NULL COMMENT '来源对象ID（COURSE为课程ID，EXAM为考试ID，TRAINING为培训计划ID）',
  `condition_type`          varchar(24)  NOT NULL COMMENT '条件类型（COMPLETED/PASSED/SCORE_AT_LEAST）',
  `condition_value`        decimal(10, 2) DEFAULT NULL COMMENT '【暂时隐藏】原用于：复杂合格条件值；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `collection_instruction` longtext       DEFAULT NULL COMMENT '【暂时隐藏】原用于：证书领取说明；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_auto_issue`           tinyint      NOT NULL COMMENT '是否自动签发证书（0否 1是）',
  `valid_from`             datetime       DEFAULT NULL COMMENT '【暂时隐藏】原用于：规则有效开始；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `valid_to`               datetime       DEFAULT NULL COMMENT '【暂时隐藏】原用于：规则有效结束；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `status`                  tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_rule_id`),
  UNIQUE KEY `uk_source_rule_deleted` (`source_type`, `source_id`, `certificate_template_id`, `deleted`),
  KEY                       `idx_source_status` (`source_type`, `source_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书签发规则表';

CREATE TABLE `sts_certificate_revocation`
(
  `certificate_revocation_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '证书撤销ID（主键）',
  `organization_id`           bigint        NOT NULL COMMENT '所属机构ID',
  `certificate_id`            bigint        NOT NULL COMMENT '证书ID',
  `revoked_by`                bigint        NOT NULL COMMENT '撤销人用户ID',
  `revoked_time`              datetime      NOT NULL COMMENT '撤销时间',
  `reason`                    varchar(1000) NOT NULL COMMENT '原因',
  `before_status`             varchar(16)   NOT NULL COMMENT '变更前证书状态（ISSUED/REVOKED/EXPIRED）',
  `after_status`              varchar(16)   NOT NULL COMMENT '变更后证书状态（ISSUED/REVOKED/EXPIRED）',
  `create_by`                 bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`               datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`certificate_revocation_id`),
  KEY                         `idx_certificate_time` (`certificate_id`, `revoked_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：证书撤销记录；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_certificate_template`
(
  `certificate_template_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '证书模板ID（主键）',
  `organization_id`         bigint        NOT NULL COMMENT '所属机构ID',
  `template_code`           varchar(64)   NOT NULL COMMENT '模板编码',
  `template_name`           varchar(100)  NOT NULL COMMENT '模板名称',
  `current_version_no`      int           NOT NULL COMMENT '当前版本号',
  `status`                  tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`             varchar(1000) NOT NULL COMMENT '描述',
  `create_by`               bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_template_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `template_code`, `deleted`),
  KEY                       `idx_org_status` (`organization_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书模板表';

CREATE TABLE `sts_certificate_template_element`
(
  `template_element_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '模板元素ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `template_version_id` bigint      NOT NULL COMMENT '证书模板版本ID',
  `element_type`        varchar(16) NOT NULL COMMENT '元素类型（TEXT/IMAGE/VARIABLE）',
  `variable_code`       varchar(64)          DEFAULT NULL COMMENT '变量编码',
  `static_content`      varchar(1000)        DEFAULT NULL COMMENT '静态内容',
  `x_position`          int         NOT NULL COMMENT '横向坐标',
  `y_position`          int         NOT NULL COMMENT '纵向坐标',
  `width`               int                  DEFAULT NULL COMMENT '宽度',
  `height`              int                  DEFAULT NULL COMMENT '高度',
  `font_family`         varchar(64)          DEFAULT NULL COMMENT '字体名称',
  `font_size`           int                  DEFAULT NULL COMMENT '字体大小',
  `font_color`          varchar(16)          DEFAULT NULL COMMENT '字体颜色',
  `sort_order`          int         NOT NULL COMMENT '显示顺序',
  `status`              tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_element_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  KEY                   `idx_template_sort` (`template_version_id`, `sort_order`),
  KEY                   `idx_variable` (`variable_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书模板元素表';

CREATE TABLE `sts_certificate_template_version`
(
  `template_version_id`     bigint      NOT NULL AUTO_INCREMENT COMMENT '证书模板版本ID（主键）',
  `organization_id`         bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `certificate_template_id` bigint      NOT NULL COMMENT '证书模板ID',
  `version_no`              int         NOT NULL COMMENT '版本号',
  `background_file_id`      bigint      NOT NULL COMMENT '背景图片文件ID',
  `width_px`                int         NOT NULL COMMENT '模板宽度像素',
  `height_px`               int         NOT NULL COMMENT '模板高度像素',
  `published_status`        varchar(16) NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `published_by`            bigint       DEFAULT NULL COMMENT '发布人用户ID（发布后必填）',
  `published_time`          datetime     DEFAULT NULL COMMENT '发布时间（发布后必填）',
  `version_note`            varchar(500) DEFAULT NULL COMMENT '版本说明',
  `create_by`               bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`template_version_id`),
  KEY                       `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_template_version` (`certificate_template_id`, `version_no`),
  KEY                       `idx_background_file` (`background_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：签发时冻结模板内容；不提供独立页面或对外操作';
