-- 清理「来源标识错配（数值撞车）」产生的证书脏数据
--
-- 背景：改造前自动签发用执行实例 ID 充当规则来源 ID，导致培训计划内的课程/考试完成时，
--       以 course_offering_id / assessment_execution_id 去撞 COURSE / EXAM 规则，偶然命中。
--
-- 逐条依据（2026-09-24 只读核对，库 19.191.192.146:8045/sts）：
--   certificate_id=3  CERT-25F81D1242D442D5  COURSE/2  rule 4  持有者 1
--     source_result_id=3 → sts_course_enrollment 3：course_offering_id=2、training_participant_id=1，
--     属培训计划 1 的开课实例（plan_content 9），并非「不属于培训计划」的独立课程学习。
--   certificate_id=4  CERT-63E04EB4F8A04B4C  EXAM/38   rule 5  持有者 1
--     source_result_id=50 → sts_exam_candidate 50：assessment_execution_id=38、assignment_source=TRAINING，
--     属培训计划 1 的派生考试考生，并非独立指派考生。
--
-- 处置：与项目逻辑删除约定一致（BaseEntity @TableLogic value=0 / delval=0，实际删除值由
--       BaseServiceImpl.removeById() 注入主键 ID），此处以 deleted = certificate_id 标记，保留可追溯性。
--       证书文件对象、规则配置与其余历史证书均不动。
--
-- 执行前请先跑下方 SELECT 复核条数与内容。

-- 复核（应返回 2 行，deleted 均为 0）
SELECT certificate_id, certificate_no, certificate_rule_id, holder_user_id,
       source_type, source_id, source_result_id, certificate_status, deleted
FROM sts_certificate_certificate
WHERE certificate_id IN (3, 4);

-- 执行清理
UPDATE sts_certificate_certificate
SET deleted = certificate_id
WHERE deleted = 0
  AND certificate_id IN (3, 4);

-- 复验（应返回 0 行未删除记录）
SELECT certificate_id, certificate_no, source_type, source_id, deleted
FROM sts_certificate_certificate
WHERE certificate_id IN (3, 4);
