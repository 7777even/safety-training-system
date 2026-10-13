-- Integration-test schema: training execution
SET NAMES utf8mb4;

CREATE TABLE `sts_training_type`
(
  `training_type_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '培训类型ID（主键）',
  `organization_id`  bigint        NOT NULL COMMENT '所属机构ID',
  `type_code`        varchar(64)   NOT NULL COMMENT '类型编码',
  `type_name`        varchar(100)  NOT NULL COMMENT '类型名称',
  `work_type`        varchar(100)           DEFAULT NULL COMMENT '工种类型（使用培训工种字典值）',
  `tag`              varchar(64)            DEFAULT NULL COMMENT '标签',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面文件ID',
  `initial_hours`    decimal(8, 2)          DEFAULT NULL COMMENT '初次学时',
  `review_hours`     decimal(8, 2)          DEFAULT NULL COMMENT '复审学时',
  `online_max_ratio` decimal(7, 4)          DEFAULT NULL COMMENT '线上最大比例',
  `status`           tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`      varchar(1000) NOT NULL COMMENT '描述',
  `create_by`        bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_type_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `type_code`, `deleted`),
  KEY                `idx_org_work_status` (`organization_id`, `work_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训类型表';

-- 正式特种作业培训类型（稳定 type_code，按企业租户幂等写入）
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_ELEC_HV',
       '高压电工作业',
       '电工作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：高压电工作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_ELEC_HV'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_ELEC_LV',
       '低压电工作业',
       '电工作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：低压电工作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_ELEC_LV'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_ELEC_EX',
       '防爆电气作业',
       '电工作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：防爆电气作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_ELEC_EX'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_WELD_MELT',
       '熔化焊接与热切割作业',
       '焊接与热切割作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：熔化焊接与热切割作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_WELD_MELT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_WELD_PRESS',
       '压力焊作业',
       '焊接与热切割作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：压力焊作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_WELD_PRESS'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_WELD_BRAZE',
       '钎焊作业',
       '焊接与热切割作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：钎焊作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_WELD_BRAZE'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_HEIGHT_ERECT',
       '登高架设作业',
       '高处作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：登高架设作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_HEIGHT_ERECT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_HEIGHT_INSTALL',
       '高处安装维护拆除作业',
       '高处作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：高处安装维护拆除作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_HEIGHT_INSTALL'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_HVAC_OP',
       '制冷与空调设备运行操作作业',
       '制冷与空调作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：制冷与空调设备运行操作作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_HVAC_OP'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_HVAC_MAINT',
       '制冷与空调设备安装修理作业',
       '制冷与空调作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：制冷与空调设备安装修理作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_HVAC_MAINT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_ELEC',
       '煤矿井下电气作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿井下电气作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_ELEC'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_BLAST',
       '煤矿井下爆破作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿井下爆破作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_BLAST'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_MONITOR',
       '煤矿安全监测监控作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿安全监测监控作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_MONITOR'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_GAS',
       '煤矿瓦斯检查作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿瓦斯检查作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_GAS'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_INSPECT',
       '煤矿安全检查作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿安全检查作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_INSPECT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_HOIST',
       '煤矿提升机操作作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿提升机操作作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_HOIST'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_COAL_SHEARER',
       '煤矿采煤机掘进机操作作业',
       '煤矿安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：煤矿采煤机掘进机操作作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_COAL_SHEARER'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_VENT',
       '金属非金属矿井通风作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿井通风作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_VENT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_TAIL',
       '尾矿作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：尾矿作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_TAIL'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_INSPECT',
       '金属非金属矿山安全检查作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿山安全检查作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_INSPECT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_HOIST',
       '金属非金属矿山提升机操作作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿山提升机操作作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_HOIST'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_SUPPORT',
       '金属非金属矿山支柱作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿山支柱作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_SUPPORT'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_ELEC',
       '金属非金属矿山井下电气作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿山井下电气作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_ELEC'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_MINE_DRAIN',
       '金属非金属矿山排水作业',
       '金属非金属矿山安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：金属非金属矿山排水作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_MINE_DRAIN'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_OIL_WELL_CONTROL',
       '石油天然气井控作业',
       '石油天然气安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：石油天然气井控作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_OIL_WELL_CONTROL'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_CHEM_FILL',
       '危险化学品充装作业',
       '危险化学品安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：危险化学品充装作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_CHEM_FILL'
                    AND t.`deleted` = 0);
INSERT INTO `sts_training_type`
(`organization_id`, `type_code`, `type_name`, `work_type`, `tag`, `initial_hours`, `review_hours`, `online_max_ratio`,
 `status`, `description`)
SELECT o.`organization_id`,
       'SO_FIREWORK_STORE',
       '烟花爆竹储存作业',
       '烟花爆竹安全作业',
       '特种作业',
       80.00,
       24.00,
       0.3000,
       1,
       '特种作业人员培训类型：烟花爆竹储存作业'
FROM `sts_org_organization` o
WHERE o.`org_type` = 'ENTERPRISE'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_training_type` t
                  WHERE t.`organization_id` = o.`organization_id`
                    AND t.`type_code` = 'SO_FIREWORK_STORE'
                    AND t.`deleted` = 0);

CREATE TABLE `sts_training_standard`
(
  `training_standard_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训标准ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属机构ID',
  `standard_code`        varchar(64)  NOT NULL COMMENT '标准编码',
  `standard_name`        varchar(100) NOT NULL COMMENT '标准名称',
  `training_type_id`     bigint       NOT NULL COMMENT '培训类型ID',
  `standard_version`     varchar(32)  NOT NULL COMMENT '标准版本',
  `valid_from`           date                  DEFAULT NULL COMMENT '有效开始',
  `valid_to`             date                  DEFAULT NULL COMMENT '有效结束',
  `publish_status`       varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_standard_id`),
  UNIQUE KEY `uk_org_code_version_deleted` (`organization_id`, `standard_code`, `standard_version`, `deleted`),
  KEY                    `idx_type_valid` (`training_type_id`, `valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训标准表';

CREATE TABLE `sts_training_standard_rule`
(
  `standard_rule_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '标准规则ID（主键）',
  `organization_id`      bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_standard_id` bigint        NOT NULL COMMENT '培训标准ID',
  `rule_type`            varchar(32)   NOT NULL COMMENT '规则类型（TOTAL_HOURS/ONLINE_RATIO/COURSE/EXAM/OFFLINE）',
  `required_value`       decimal(12, 4)         DEFAULT NULL COMMENT '要求值',
  `unit`                 varchar(16)            DEFAULT NULL COMMENT '单位',
  `resource_type`        varchar(32)            DEFAULT NULL COMMENT '资源类型（COURSE/EXAM/OFFLINE）',
  `resource_id`          bigint                 DEFAULT NULL COMMENT '资源ID',
  `sort_order`           int           NOT NULL COMMENT '显示顺序',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`          varchar(1000) NOT NULL COMMENT '描述',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`standard_rule_id`),
  KEY                    `idx_organization_id` (`organization_id`),
  KEY                    `idx_standard_type` (`training_standard_id`, `rule_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训标准规则表';


CREATE TABLE `sts_training_template_category`
(
  `template_category_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '模板分类ID（主键）',
  `organization_id`      bigint        NOT NULL COMMENT '所属机构ID',
  `parent_id`            bigint        NOT NULL DEFAULT 0 COMMENT '父级ID',
  `ancestors`            varchar(1000) NOT NULL COMMENT '祖级路径',
  `category_name`        varchar(100)  NOT NULL COMMENT '分类名称',
  `sort_order`           int           NOT NULL COMMENT '显示顺序',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_category_id`),
  UNIQUE KEY `uk_org_parent_name_deleted` (`organization_id`, `parent_id`, `category_name`, `deleted`),
  KEY                    `idx_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板分类表';

CREATE TABLE `sts_training_template`
(
  `training_template_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训模板ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属机构ID',
  `template_code`        varchar(64)  NOT NULL COMMENT '模板编码',
  `template_name`        varchar(100) NOT NULL COMMENT '模板名称',
  `template_category_id` bigint                DEFAULT NULL COMMENT '模板分类ID',
  `training_type_id`     bigint       NOT NULL COMMENT '培训类型ID',
  `training_standard_id` bigint                DEFAULT NULL COMMENT '培训标准ID',
  `description`          longtext              DEFAULT NULL COMMENT '描述',
  `publish_status`       varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_template_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `template_code`, `deleted`),
  KEY                    `idx_type_status` (`training_type_id`, `publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板表';

CREATE TABLE `sts_training_template_stage`
(
  `template_stage_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '模板阶段ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_template_id` bigint       NOT NULL COMMENT '培训模板ID',
  `stage_name`           varchar(100) NOT NULL COMMENT '阶段名称',
  `stage_type`           varchar(16)  NOT NULL COMMENT '阶段类型（ONLINE/OFFLINE/MIXED）',
  `required_hours`       decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`           int          NOT NULL COMMENT '显示顺序',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_stage_id`),
  KEY                    `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_template_stage_name_deleted` (`training_template_id`, `stage_name`, `deleted`),
  KEY                    `idx_template_sort` (`training_template_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板阶段表';

CREATE TABLE `sts_training_template_content`
(
  `template_content_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '模板内容ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `template_stage_id`   bigint       NOT NULL COMMENT '模板阶段ID',
  `content_type`        varchar(24)  NOT NULL COMMENT '内容类型（COURSE/EXAM/QUESTION_BANK/OFFLINE）',
  `resource_id`         bigint                DEFAULT NULL COMMENT '资源ID',
  `resource_version_id`   bigint      DEFAULT NULL COMMENT '冻结资源版本ID（COURSE为courseVersionId，EXAM为paperVersionId）',
  `assessment_mode`       varchar(16) DEFAULT NULL COMMENT '考试模式（QUIZ/FORMAL_EXAM，仅EXAM使用）',
  `execution_config_json` json        DEFAULT NULL COMMENT '内容执行配置JSON',
  `content_name`        varchar(150) NOT NULL COMMENT '内容名称',
  `is_required`         tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `required_hours`      decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`          int          NOT NULL COMMENT '显示顺序',
  `status`              tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_content_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  KEY                   `idx_stage_sort` (`template_stage_id`, `sort_order`),
  KEY                   `idx_resource` (`content_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板内容表';

CREATE TABLE `sts_training_plan`
(
  `training_plan_id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '培训计划ID（主键）',
  `organization_id`                 bigint       NOT NULL COMMENT '所属机构ID',
  `plan_code`                       varchar(64)  NOT NULL COMMENT '计划编码',
  `plan_name`                       varchar(150) NOT NULL COMMENT '计划名称',
  `training_type_id`                bigint       NOT NULL COMMENT '培训类型ID',
  `training_nature`           varchar(16) NOT NULL COMMENT '培训性质（INITIAL初培 REVIEW复审；UNSPECIFIED仅迁移历史计划）',
  `applicable_required_hours` decimal(8, 2) DEFAULT NULL COMMENT '计划发布时冻结的适用最低学时',
  `training_standard_id`            bigint                DEFAULT NULL COMMENT '培训标准ID',
  `source_template_id`              bigint                DEFAULT NULL COMMENT '来源培训模板ID',
  `source_owner_organization_id`    bigint                DEFAULT NULL COMMENT '发布快照中的来源资源所属机构ID（当前仅允许本机构）',
  `training_type_snapshot_json`     json                  DEFAULT NULL COMMENT '计划发布时冻结的培训类型快照JSON',
  `training_standard_snapshot_json` json                  DEFAULT NULL COMMENT '计划发布时冻结的培训标准及规则快照JSON',
  `template_snapshot_json`          json                  DEFAULT NULL COMMENT '计划发布时冻结的来源模板快照JSON',
  `source_snapshot_hash`            varchar(128)          DEFAULT NULL COMMENT '来源快照校验哈希',
  `manager_user_id`                 bigint                DEFAULT NULL COMMENT '培训负责人用户ID',
  `start_time`                      datetime     NOT NULL COMMENT '开始时间',
  `end_time`                        datetime     NOT NULL COMMENT '结束时间',
  `location`                        varchar(255)          DEFAULT NULL COMMENT '地点',
  `description`                     longtext              DEFAULT NULL COMMENT '描述',
  `publish_status`                  varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/RUNNING/ENDED/CANCELLED/ARCHIVED）',
  `published_time`                  datetime              DEFAULT NULL COMMENT '发布时间',
  `ended_time`                      datetime              DEFAULT NULL COMMENT '结束时间',
  `status`                          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_plan_id`),
  UNIQUE KEY `uk_org_plan_code_deleted` (`organization_id`, `plan_code`, `deleted`),
  KEY                               `idx_org_status_time` (`organization_id`, `publish_status`, `start_time`, `end_time`),
  KEY                               `idx_training_type` (`training_type_id`),
  KEY                               `idx_standard` (`training_standard_id`),
  KEY                               `idx_source_template` (`source_template_id`),
  KEY                               `idx_source_owner` (`source_owner_organization_id`),
  KEY                               `idx_manager` (`manager_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划表';

CREATE TABLE `sts_training_plan_organization`
(
  `plan_organization_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '计划机构ID（主键）',
  `organization_id`             bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`            bigint      NOT NULL COMMENT '培训计划ID',
  `participant_organization_id` bigint      NOT NULL COMMENT '参与机构ID',
  `organization_role`           varchar(16) NOT NULL COMMENT '机构参与角色（ORGANIZER/PARTICIPANT）',
  `create_by`                   bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`plan_organization_id`),
  KEY                           `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_plan_org` (`training_plan_id`, `participant_organization_id`, `organization_role`),
  KEY                           `idx_org_plan` (`participant_organization_id`, `training_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划机构关联表';

CREATE TABLE `sts_training_plan_stage`
(
  `plan_stage_id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '计划阶段ID（主键）',
  `organization_id`          bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`         bigint       NOT NULL COMMENT '培训计划ID',
  `source_template_stage_id` bigint                DEFAULT NULL COMMENT '来源模板阶段ID',
  `stage_name`               varchar(100) NOT NULL COMMENT '阶段名称',
  `stage_type`               varchar(16)  NOT NULL COMMENT '阶段类型（ONLINE/OFFLINE/MIXED）',
  `start_time`               datetime              DEFAULT NULL COMMENT '开始时间',
  `end_time`                 datetime              DEFAULT NULL COMMENT '结束时间',
  `location`                 varchar(255)          DEFAULT NULL COMMENT '地点',
  `required_hours`           decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`               int          NOT NULL COMMENT '显示顺序',
  `stage_status`             varchar(16)  NOT NULL COMMENT '阶段状态（PENDING/RUNNING/COMPLETED/CANCELLED）',
  `create_by`                bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`              datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                  int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_stage_id`),
  KEY                        `idx_organization_id` (`organization_id`),
  KEY                        `idx_plan_sort` (`training_plan_id`, `sort_order`),
  KEY                        `idx_stage_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划阶段表';

CREATE TABLE `sts_training_plan_content`
(
  `plan_content_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '计划内容ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `plan_stage_id`          bigint       NOT NULL COMMENT '计划阶段ID',
  `content_type`           varchar(24)  NOT NULL COMMENT '内容类型（COURSE/EXAM/QUESTION_BANK/OFFLINE）',
  `resource_id`            bigint                DEFAULT NULL COMMENT '资源ID',
  `resource_version_id`    bigint       DEFAULT NULL COMMENT '冻结资源版本ID（COURSE为courseVersionId，EXAM为paperVersionId）',
  `assessment_mode`        varchar(16)  DEFAULT NULL COMMENT '考试模式（QUIZ/FORMAL_EXAM，仅EXAM使用）',
  `execution_config_json`  json         DEFAULT NULL COMMENT '内容执行配置JSON',
  `resource_snapshot_json` json         DEFAULT NULL COMMENT '计划发布时冻结的内容资源快照JSON',
  `resource_snapshot_hash` varchar(128) DEFAULT NULL COMMENT '内容资源快照校验哈希',
  `content_name`           varchar(150) NOT NULL COMMENT '内容名称',
  `is_required`            tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `is_scored`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否计入综合成绩（0否 1是）',
  `score_weight`           decimal(7, 4)         DEFAULT NULL COMMENT '同类型内容聚合权重',
  `score_aggregation_mode` varchar(24)           DEFAULT NULL COMMENT '同类型成绩聚合方式（AVERAGE/WEIGHTED_AVERAGE）',
  `required_hours`         decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `content_status`         varchar(16)  NOT NULL COMMENT '内容状态（PENDING/RUNNING/COMPLETED/FAILED/SKIPPED）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_content_id`),
  KEY                      `idx_organization_id` (`organization_id`),
  KEY                      `idx_stage_sort` (`plan_stage_id`, `sort_order`),
  KEY                      `idx_resource` (`content_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划内容表';

-- Audit class C
CREATE TABLE `sts_training_plan_question_pool`
(
  `plan_question_pool_id`    bigint      NOT NULL AUTO_INCREMENT COMMENT '计划冻结题池行ID',
  `organization_id`          bigint      NOT NULL COMMENT '所属机构ID',
  `training_plan_id`         bigint      NOT NULL COMMENT '培训计划ID',
  `plan_content_id`          bigint      NOT NULL COMMENT '计划内容ID',
  `question_bank_id`         bigint      NOT NULL COMMENT '题库ID',
  `question_id`              bigint      NOT NULL COMMENT '题目ID',
  `question_version_id`      bigint      NOT NULL COMMENT '发布时冻结题目版本ID',
  `question_type`            varchar(16) NOT NULL COMMENT '题目类型',
  `difficulty`               varchar(16) DEFAULT NULL COMMENT '难度',
  `knowledge_point_ids_json` json        NOT NULL COMMENT '知识点ID快照',
  `evaluation_mode`          varchar(16) NOT NULL COMMENT '判定模式（AUTO/RECORD_ONLY）',
  `candidate_order`          int         NOT NULL COMMENT '候选稳定顺序',
  `pool_hash`                char(64)    NOT NULL COMMENT '候选池SHA-256指纹',
  `create_by`                bigint      DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`plan_question_pool_id`),
  UNIQUE KEY `uk_plan_content_question` (`plan_content_id`, `question_id`),
  KEY                        `idx_plan_content_order` (`training_plan_id`, `plan_content_id`, `candidate_order`),
  KEY                        `idx_question_version` (`question_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划发布冻结题池';

CREATE TABLE `sts_training_plan_evaluation_form`
(
  `plan_evaluation_form_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '培训计划评价表单关联ID（主键）',
  `organization_id`         bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`        bigint      NOT NULL COMMENT '培训计划ID',
  `form_version_id`         bigint      NOT NULL COMMENT '评价表单版本ID',
  `usage_type`              varchar(24) NOT NULL COMMENT '评价用途（COMPOSITE/EXPERT_INSPECTION/PARTICIPANT/COURSE_QUALITY）',
  `is_required`             tinyint     NOT NULL COMMENT '是否必填（0否 1是）',
  `status`                  tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_evaluation_form_id`),
  KEY                       `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_plan_usage_deleted` (`training_plan_id`, `usage_type`, `deleted`),
  KEY                       `idx_form_plan` (`form_version_id`, `training_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划评价表单关联表';

CREATE TABLE `sts_training_participant`
(
  `training_participant_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '培训参与人员ID（主键）',
  `organization_id`             bigint        NOT NULL COMMENT '所属机构ID',
  `training_plan_id`            bigint        NOT NULL COMMENT '培训计划ID',
  `user_id`                     bigint        NOT NULL COMMENT '用户ID',
  `participant_org_id`          bigint        NOT NULL COMMENT '参训人员所属机构ID',
  `join_source`                 varchar(16)   NOT NULL COMMENT '加入来源（ASSIGN/IMPORT/QR）',
  `joined_time`                 datetime      NOT NULL COMMENT '加入时间',
  `participant_status`          varchar(24)   NOT NULL COMMENT '参与人员状态（PENDING/LEARNING/COMPLETED/FAILED/EXPIRED/WITHDRAWN）',
  `online_hours`                decimal(8, 2) NOT NULL COMMENT '线上学时',
  `offline_hours`               decimal(8, 2) NOT NULL COMMENT '线下学时',
  `course_score`                decimal(10, 2)         DEFAULT NULL COMMENT '课程分数',
  `exam_score`                  decimal(10, 2)         DEFAULT NULL COMMENT '考试分数',
  `offline_score`               decimal(10, 2)         DEFAULT NULL COMMENT '线下培训成绩',
  `composite_score`             decimal(10, 2) DEFAULT NULL COMMENT '【内部保留】用于：参训人当前综合分，作为页面唯一读取口径；不提供独立页面或对外操作',
  `official_calculation_run_id` bigint         DEFAULT NULL COMMENT '【暂时隐藏】原用于：当前正式综合成绩计算批次ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `result_status`               varchar(16)            DEFAULT NULL COMMENT '培训结果状态（PASSED/FAILED/INCOMPLETE）',
  `completed_time`              datetime               DEFAULT NULL COMMENT '完成时间',
  `final_result_snapshot_json` longtext DEFAULT NULL COMMENT '结束时最终结果快照JSON',
  `final_result_snapshot_hash` char(64) DEFAULT NULL COMMENT '规范化最终结果快照哈希',
  `result_finalized_time`      datetime DEFAULT NULL COMMENT '结果冻结时间',
  `create_by`                   bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                   bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                 datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                     int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_participant_id`),
  UNIQUE KEY `uk_plan_user_deleted` (`training_plan_id`, `user_id`, `deleted`),
  KEY                           `idx_user_status` (`user_id`, `participant_status`),
  KEY                           `idx_plan_org_status` (`training_plan_id`, `participant_org_id`, `participant_status`),
  KEY                           `idx_org_status_time` (`organization_id`, `participant_status`, `joined_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训参与人员表';

CREATE TABLE `sts_training_participant_content_result`
(
  `training_participant_content_result_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '培训参训人内容结果投影ID（主键）',
  `organization_id`                        bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`                       bigint      NOT NULL COMMENT '培训计划ID',
  `training_participant_id`                bigint      NOT NULL COMMENT '培训参与人员ID',
  `plan_content_id`                        bigint      NOT NULL COMMENT '计划内容ID',
  `content_type`                           varchar(24) NOT NULL COMMENT '内容类型（COURSE/EXAM/QUESTION_BANK/OFFLINE）',
  `execution_id`                           bigint               DEFAULT NULL COMMENT '执行实例ID',
  `source_record_type` varchar(32) DEFAULT NULL COMMENT '来源记录类型（COURSE_ENROLLMENT/EXAM_CANDIDATE/OFFLINE_SCORE）',
  `source_record_id`                       bigint               DEFAULT NULL COMMENT '来源记录ID',
  `final_attempt_id`                       bigint               DEFAULT NULL COMMENT '最终作答尝试ID（非考试类型为空）',
  `evaluated_count`  int           DEFAULT NULL COMMENT '题库练习可判断题数',
  `correct_count`    int           DEFAULT NULL COMMENT '题库练习自动判断正确数',
  `wrong_count`      int           DEFAULT NULL COMMENT '题库练习自动判断错误数',
  `unassessed_count` int           DEFAULT NULL COMMENT '题库练习只记录作答、不判断对错的题目数',
  `accuracy_rate`    decimal(7, 2) DEFAULT NULL COMMENT '题库练习可判断题正确率百分比',
  `result_status`                          varchar(24) NOT NULL COMMENT '结果状态（PENDING/IN_PROGRESS/PENDING_PUBLISH/COMPLETED/FAILED/EXPIRED）',
  `source_status`                          varchar(32)          DEFAULT NULL COMMENT '来源记录原始状态',
  `effective_duration_seconds`             int         NOT NULL DEFAULT 0 COMMENT '有效学时秒数',
  `score`                                  decimal(10, 2)       DEFAULT NULL COMMENT '分项成绩',
  `completed_time`                         datetime             DEFAULT NULL COMMENT '完成时间',
  `source_updated_time`                    datetime             DEFAULT NULL COMMENT '来源最后更新时间',
  `synchronized_time`                      datetime             DEFAULT NULL COMMENT '最近同步时间',
  `result_revision`                        int         NOT NULL DEFAULT 0 COMMENT '结果修订号',
  `finalized_time`                         datetime             DEFAULT NULL COMMENT '结束冻结时间',
  `create_by`                              bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                            datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                              bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                            datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                                bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                                int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_participant_content_result_id`),
  KEY                                      `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_participant_content_deleted` (`training_participant_id`, `plan_content_id`, `deleted`),
  KEY                                      `idx_plan_status` (`training_plan_id`, `result_status`),
  KEY                                      `idx_content_status` (`plan_content_id`, `result_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训参训人逐内容结果投影表';

CREATE TABLE `sts_training_attachment`
(
  `training_attachment_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训附件ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`       bigint       NOT NULL COMMENT '培训计划ID',
  `plan_stage_id`          bigint                DEFAULT NULL COMMENT '培训计划阶段ID',
  `attachment_type`        varchar(24)  NOT NULL COMMENT '附件类型（GENERAL/IMAGE/VIDEO/EVALUATION）',
  `file_info_id`           bigint       NOT NULL COMMENT '文件信息ID',
  `attachment_name`        varchar(255) NOT NULL COMMENT '附件名称',
  `description`            varchar(1000)         DEFAULT NULL COMMENT '描述',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `status`                 tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_attachment_id`),
  KEY                      `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_plan_file_deleted` (`training_plan_id`, `file_info_id`, `deleted`),
  KEY                      `idx_plan_type` (`training_plan_id`, `attachment_type`),
  KEY                      `idx_stage` (`plan_stage_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训附件表';

CREATE TABLE `sts_training_execution_link`
(
  `execution_link_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '执行关联ID（主键）',
  `organization_id`   bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`  bigint      NOT NULL COMMENT '培训计划ID',
  `plan_content_id`   bigint      NOT NULL COMMENT '计划内容ID',
  `execution_type`    varchar(24) NOT NULL COMMENT '执行类型（COURSE/EXAM/QUESTION_BANK/OFFLINE）',
  `execution_id`      bigint      NOT NULL COMMENT '执行实例ID',
  `create_by`         bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`       datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`execution_link_id`),
  KEY                 `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_content_type` (`plan_content_id`, `execution_type`),
  KEY                 `idx_plan` (`training_plan_id`),
  KEY                 `idx_execution` (`execution_type`, `execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划内容执行关联表';

CREATE TABLE `sts_training_offline_session`
(
  `offline_session_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '线下培训场次ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_plan_id`   bigint       NOT NULL COMMENT '培训计划ID',
  `plan_stage_id`      bigint       NOT NULL COMMENT '计划阶段ID',
  `plan_content_id`    bigint                DEFAULT NULL COMMENT '培训计划内容ID',
  `session_name`       varchar(150) NOT NULL COMMENT '线下培训场次名称',
  `teacher_user_id`    bigint                DEFAULT NULL COMMENT '讲师用户ID',
  `location`           varchar(255) NOT NULL COMMENT '地点',
  `start_time`         datetime     NOT NULL COMMENT '开始时间',
  `end_time`           datetime     NOT NULL COMMENT '结束时间',
  `session_status`     varchar(16)  NOT NULL COMMENT '场次状态（DRAFT/SCHEDULED/RUNNING/ENDED/CANCELLED）',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`offline_session_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  KEY                  `idx_stage_time` (`plan_stage_id`, `start_time`),
  KEY                  `idx_teacher_time` (`teacher_user_id`, `start_time`),
  UNIQUE KEY `uk_plan_content_deleted` (`plan_content_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线下培训场次表';

CREATE TABLE `sts_training_offline_score`
(
  `offline_score_id`        bigint         NOT NULL AUTO_INCREMENT COMMENT '线下培训成绩ID（主键）',
  `organization_id`         bigint         NOT NULL COMMENT '所属机构ID',
  `training_participant_id` bigint         NOT NULL COMMENT '培训参与人员ID',
  `offline_session_id`      bigint         NOT NULL COMMENT '线下培训场次ID',
  `score_name`              varchar(100)   NOT NULL COMMENT '成绩项名称',
  `score`                   decimal(10, 2) NOT NULL COMMENT '分数',
  `max_score`               decimal(10, 2) NOT NULL COMMENT '满分',
  `score_source`            varchar(16)    NOT NULL COMMENT '成绩来源（MANUAL/IMPORT）',
  `import_batch_id`         bigint                  DEFAULT NULL COMMENT '导入批次ID',
  `scored_by`               bigint                  DEFAULT NULL COMMENT '评分人用户ID',
  `scored_time`             datetime       NOT NULL COMMENT '评分时间',
  `create_by`               bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`offline_score_id`),
  UNIQUE KEY `uk_participant_session_name_deleted` (`training_participant_id`, `offline_session_id`, `score_name`, `deleted`),
  KEY                       `idx_session_score` (`offline_session_id`, `score`),
  KEY                       `idx_org_scored_time` (`organization_id`, `scored_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线下培训成绩表';

CREATE TABLE `sts_training_reward_penalty`
(
  `reward_penalty_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '培训奖惩记录ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `training_plan_id`        bigint       NOT NULL COMMENT '培训计划ID',
  `training_participant_id` bigint       NOT NULL COMMENT '培训参与人员ID',
  `record_type`             varchar(16)  NOT NULL COMMENT '记录类型（REWARD/PENALTY）',
  `title`                   varchar(200) NOT NULL COMMENT '标题',
  `description`             longtext     NULL COMMENT '描述',
  `score_change`            decimal(10, 2)        DEFAULT NULL COMMENT '分数变动',
  `occurred_date`           date         NULL COMMENT '发生日期',
  `evidence_file_id`        bigint                DEFAULT NULL COMMENT '证据文件ID',
  `status`                  tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`reward_penalty_id`),
  KEY                       `idx_participant_date` (`training_participant_id`, `occurred_date`),
  KEY                       `idx_plan_type` (`training_plan_id`, `record_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训奖惩记录表';

CREATE TABLE `sts_training_participant_evaluation`
(
  `participant_evaluation_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '参训人员评价记录ID（主键）',
  `organization_id`           bigint      NOT NULL COMMENT '所属机构ID',
  `training_plan_id`          bigint      NOT NULL COMMENT '培训计划ID',
  `training_participant_id`   bigint      NOT NULL COMMENT '培训参与人员ID',
  `evaluator_user_id`         bigint      NOT NULL COMMENT '评价人用户ID',
  `evaluation_record_id`      bigint               DEFAULT NULL COMMENT '结构化评价记录ID',
  `evaluation_type`           varchar(24) NOT NULL COMMENT '评价类型（TEACHER/ORGANIZATION/SELF/OTHER）',
  `score`                     decimal(10, 2)       DEFAULT NULL COMMENT '分数',
  `content`                   longtext             DEFAULT NULL COMMENT '内容',
  `evaluated_time`            datetime    NOT NULL COMMENT '评价时间',
  `status`                    tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                 bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`               datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                 bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`               datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                   int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`participant_evaluation_id`),
  KEY                         `idx_participant_type` (`training_participant_id`, `evaluation_type`),
  KEY                         `idx_plan_time` (`training_plan_id`, `evaluated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参训人员评价记录表';

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
