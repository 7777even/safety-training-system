-- 题库练习抽题规则：独立 BANK_PRACTICE 改按抽题规则抽取题目，取代「全题库每题 1 分全量冻结」。
-- 该列为空表示未配置规则，发布时由 ExamServiceImpl 拦截为业务码 7063。
ALTER TABLE `sts_exam_exam`
  ADD COLUMN `draw_rule_json` json DEFAULT NULL COMMENT '题库练习抽题规则（仅BANK_PRACTICE，结构与培训侧drawRules一致）' AFTER `status`;

-- 对齐契约：answer_detail_mode 契约取值为 SUBMITTED/PUBLISHED/NEVER，其中 PUBLISHED 表示「成绩发布后可见」。
-- 原注释列出的 PASSED/EXAM_ENDED/RETAKE_ENDED 实现仍兼容历史数据，但不属于当前契约。
ALTER TABLE `sts_exam_policy`
  MODIFY COLUMN `answer_detail_mode` varchar(24) NOT NULL COMMENT '答题详情查看模式（SUBMITTED交卷后/PUBLISHED成绩发布后/NEVER不可查看）';
