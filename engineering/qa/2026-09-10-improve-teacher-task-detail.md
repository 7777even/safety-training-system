# 教师任务详情名单与录分提示 QA

## 范围

- 教师任务详情 `roster` 返回并展示学员姓名。
- 场次状态、后端录分动作和权限拒绝的教师提示。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -Dtest=TrainingWorkflowServiceImplTest test
```

结果：27 项通过，0 项失败。覆盖教师任务名单的姓名映射与用户资料缺失时的空值降级。

在 `frontend/pc-web` 执行：

```text
node --test tests/training-teacher-task-api.test.mjs
pnpm --filter @safety-training/pc-web typecheck
```

结果：Node 3 项通过，类型检查通过。覆盖 `userName`、未开始/结束/取消/权限提示、无签到引用和后端 `availableActions` 录分边界。

此外，`docs/api/training.openapi.json` 已通过 JSON 解析，`git diff --check` 通过。

## 待人工验收

当前环境没有可用教师登录态，未执行浏览器联调。需要验证：

1. 已安排场次显示“先开始场次”提示，开始后显示名单录分入口。
2. 名单优先显示真实姓名；资料缺失时显示“未获取姓名”和用户 ID。
3. 已结束、已取消和无录分权限时提示实际原因，不显示可录分入口。
4. 无权限或非本场教师访问时显示权限提示。
