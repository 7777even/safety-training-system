# Retro：教师任务返回场次乐观锁 version

## 做得好的地方

- 写接口要 `version` 时，同步检查教师可读入口是否给出令牌，而不是让教师去调管理端场次详情。

## 问题

- 状态机落地时只补了 `availableActions` 和写接口 body，漏了读侧 `version`。

## 原因

教师任务 VO 早于状态机存在；推进接口复用了 `TrainingPublishRequest.version`，没有反向核对教师任务契约。

## 改进方案

状态推进类接口：读模型必须返回乐观锁版本；学员等只读入口继续不返回。
