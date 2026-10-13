ALTER TABLE `sts_archive_version`
  ADD COLUMN `file_info_id` bigint DEFAULT NULL COMMENT '当前归档版本生成文件ID' AFTER `summary_snapshot_json`;
