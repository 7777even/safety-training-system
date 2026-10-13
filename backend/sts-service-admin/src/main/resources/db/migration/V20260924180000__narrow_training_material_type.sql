-- 培训资料类型收口：奖惩佐证统一由奖惩记录自身的佐证文件承载，「奖惩佐证」不再作为资料类型。
-- 既有 attachment_type = 'REWARD_PENALTY' 的资料改写为 'GENERAL'（保留记录与文件，不删除任何事实）。
--
-- 回滚范围：本迁移是**有损改写**，没有自动可逆路径。历史资料与奖惩记录之间不存在可判定的对应关系，
-- 因此迁移不尝试把历史资料自动挂接到某条奖惩记录上（自动绑定会伪造事实）；
-- 如需还原只能在归档核对后人工重建，且无法区分哪些 GENERAL 原本是 REWARD_PENALTY。
-- 若目标环境不存在 REWARD_PENALTY 资料，本迁移为零影响。
UPDATE `sts_training_attachment`
SET `attachment_type` = 'GENERAL'
WHERE `attachment_type` = 'REWARD_PENALTY';

ALTER TABLE `sts_training_attachment`
  MODIFY COLUMN `attachment_type` varchar(24) NOT NULL COMMENT '附件类型（GENERAL/IMAGE/VIDEO/EVALUATION）';
