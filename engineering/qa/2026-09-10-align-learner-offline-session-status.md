# 学员端线下场次状态对齐 QA

## 范围

- 学员结果项对线下内容返回当前场次状态，个人完成状态保持原有成绩语义。
- PC Web 线下内容卡展示场次状态，线下详情分别展示场次状态与完成状态。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -Dtest=TrainingResultSyncServiceImplTest test
```

结果：26 项通过，0 项失败。覆盖进行中场次与未评分个人结果并存、无关联场次的空值降级，以及非线下内容不返回场次状态。

在 `frontend/pc-web` 执行：

```text
pnpm typecheck
pnpm exec node --test tests/training-learner-api.test.mjs
```

结果：类型检查通过；Node 4 项通过，0 项失败。覆盖线下内容优先显示 `IN_PROGRESS`、课程仍显示个人 `PENDING`，以及线下详情的双状态展示。

此外，`docs/api/training.openapi.json` JSON 解析通过，`git diff --check` 通过。

## 待人工验收

当前环境没有可用学员登录态。需要使用同一线下场次验证：

1. 教师开始场次后，未评分学员的培训详情卡显示“进行中”，概览已完成数不增加。
2. 教师录入合格成绩后，学员的完成状态变为“已完成”，获得学时按既有规则更新。
3. 学员进入线下场次详情时，分别看到“场次状态：进行中”和“完成状态：待评分”。
