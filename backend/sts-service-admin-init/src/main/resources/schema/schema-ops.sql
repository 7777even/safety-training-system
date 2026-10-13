-- Integration-test schema: ops
SET NAMES utf8mb4;

CREATE TABLE `sts_ops_export_job`
(
  `export_job_id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '导出任务ID（主键）',
  `organization_id` bigint      NOT NULL COMMENT '所属机构ID',
  `export_domain`   varchar(32) NOT NULL COMMENT '导出域（COURSES/EXAMS/TRAININGS/RESULTS）',
  `export_format`   varchar(16) NOT NULL COMMENT '导出格式（CSV/XLSX/JSON）',
  `request_json`    json        NOT NULL COMMENT '导出请求快照JSON',
  `job_status`      varchar(16) NOT NULL COMMENT '任务状态（PENDING/RUNNING/SUCCEEDED/FAILED）',
  `result_file_id`  bigint               DEFAULT NULL COMMENT '结果文件ID',
  `failure_reason`  varchar(1000)        DEFAULT NULL COMMENT '失败原因',
  `requested_by`    bigint      NOT NULL COMMENT '请求人用户ID',
  `finished_time`   datetime             DEFAULT NULL COMMENT '完成时间',
  `create_by`       bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`export_job_id`),
  KEY               `idx_org_status_time` (`organization_id`, `job_status`, `create_time`),
  KEY               `idx_requester_time` (`requested_by`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：运营异步导出任务中心；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';
