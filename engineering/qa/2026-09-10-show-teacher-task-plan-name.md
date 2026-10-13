# 教师任务列表所属培训显示 QA

## 范围

- 教师任务分页返回所属培训名称。
- PC Web 教师任务列表显示所属培训和名称缺失降级文案。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin clean test -Dtest=TrainingTeacherTaskServiceImplTest
```

结果：3 项通过，0 项失败。清理后重新编译主代码和测试代码，验证任务摘要的培训名称批量映射，以及计划资料缺失时仍保留任务并返回空名称。

在 `frontend/pc-web` 执行：

```text
node --test tests/training-teacher-task-api.test.mjs
pnpm --filter @safety-training/pc-web typecheck
```

结果：Node 4 项通过，类型检查通过。覆盖所属培训列、缺失名称降级文案和既有教师任务边界。

## 待人工验收

当前环境没有可用教师登录态，未执行浏览器联调。需要使用有同名场次的教师账号确认“所属培训”列可准确区分任务。
