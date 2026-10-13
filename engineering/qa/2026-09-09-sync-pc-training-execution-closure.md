# PC 培训执行闭环契约同步 QA

## 范围

- 学员培训详情、考试/题库进入、学员线下场次页。
- 教师线下任务场次开始、结束、取消、名单与单人线下成绩录入。
- 培训计划执行结果的获得学时展示。

## 自动化验证

在 `frontend/pc-web` 执行：

```text
node --test tests/training-learner-api.test.mjs tests/training-teacher-task-api.test.mjs tests/training-plan-api.test.mjs tests/training-workspace-ui.test.mjs
```

结果：25 项通过，0 项失败。覆盖考试进入不传 `attemptType`、无签到 HTTP、无人工结果同步、学员专属线下接口、教师场次状态接口（含 `version`）、名单成绩接口及获得学时展示。

```text
pnpm typecheck
```

结果：通过。

2026-09-10 增量回归：

```text
node --test tests/training-learner-api.test.mjs
pnpm typecheck
```

结果：均通过。覆盖培训考试、题库练习在待阅卷、待发布、已完成、已通过和未通过状态下进入既有培训结果页，而非重新打开作答页。

```text
pnpm build -- --outDir C:\\Users\\careers\\AppData\\Local\\Temp\\codex-training-closure-build
```

结果：通过。构建输出至系统临时目录；仅有既有依赖的浏览器兼容性提示及大体积 chunk 提示，未影响构建完成。

## 待人工验收

当前环境未提供可用的学员、教师登录态，尚未执行浏览器联调。需要分别验证：

1. 学员交卷返回培训详情后，内容项状态及获得学时按后端投影刷新；再次进入已提交答卷时，进入结果页并按后端可见范围展示本人答案和题目解析。
2. 教师可从已安排状态开始场次，进行中录分并结束或取消场次；场次状态冲突时显示后端提示。
3. 教师未录分时线下项未完成；录入合格后完成并获得安排学时；录入不合格后未通过且获得学时为 0。
4. 学员线下页仅显示本人地点、时间、分数、状态和获得学时。
