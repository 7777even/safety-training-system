# Tasks：培训考试待阅卷投影

> Owner：fxw11。状态：已完成。

- [x] **培训考试状态投影**：将 `PENDING_REVIEW` 写入培训内容结果；验收：提交后不再显示待开始；测试：`TrainingResultSyncServiceImplTest`。
- [x] **PC 学员入口**：展示待阅卷并跳转只读答卷结果；验收：待阅卷或待发布不进入作答页；测试：`training-learner-api.test.mjs`、typecheck。
