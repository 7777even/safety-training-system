# Design：培训考试待阅卷投影

培训考试的状态链为：`ANSWERING → PENDING_REVIEW → PENDING_PUBLISH → COMPLETED|FAILED`。最后一道主观题批阅完成时，服务端同步更新候选人事实并触发培训内容投影刷新。

`TrainingResultSyncServiceImpl` 读取考试候选人的 `PENDING_REVIEW` 时，必须写入同名培训内容结果状态，不能回退为 `PENDING`。PC 端仅根据结果投影决定文案和跳转：待阅卷、待发布均进入答卷结果页。
