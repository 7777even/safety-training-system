# Spec Delta：教师任务返回场次乐观锁 version

## Modified Capabilities

### training-execution-loop

教师任务列表和详情 MUST 返回场次乐观锁 `version`，供 start/complete/cancel 回传。
