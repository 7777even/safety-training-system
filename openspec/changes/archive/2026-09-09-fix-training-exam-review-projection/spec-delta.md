# Spec Delta：培训执行闭环

## 培训考试结果投影

当培训考试答卷进入 `PENDING_REVIEW` 时，系统必须将该参训人的对应内容项投影为 `PENDING_REVIEW`，获得学时为 0。最后一道主观题批阅完成时，内容项必须推进为 `PENDING_PUBLISH`；成绩发布后推进为 `COMPLETED` 或 `FAILED`。学员端在前两种待处理状态仅查看答卷结果。
