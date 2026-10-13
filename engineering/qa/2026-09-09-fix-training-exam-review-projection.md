# 培训考试待阅卷投影 QA

- 后端：`mvn -Dbuildhelper.skip=true -Dtest=ExamAttemptServiceImplTest,TrainingResultSyncServiceImplTest test`，71 项通过。
- PC：`node --test tests/training-learner-api.test.mjs`，2 项通过；`pnpm typecheck` 通过。
- 人工验收：培训考试交卷后刷新详情应显示“待阅卷”；完成教师批阅后显示“待发布成绩”；发布成绩后显示“已完成”或“未通过”。
