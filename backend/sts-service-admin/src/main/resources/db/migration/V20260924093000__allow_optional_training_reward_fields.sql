-- 奖惩说明和发生日期已在 API 中定义为可选；允许创建时省略、编辑时清空。
ALTER TABLE `sts_training_reward_penalty`
  MODIFY COLUMN `description` longtext NULL COMMENT '描述',
  MODIFY COLUMN `occurred_date` date NULL COMMENT '发生日期';
