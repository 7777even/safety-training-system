# 考试试卷默认规则契约与批阅回归 QA

日期：2026-08-19  
关联 Change：`2026-08-19-sync-exam-paper-default-contract-and-review-regression`

## 覆盖范围

- 考试工作台创建、草稿编辑、详情和发布核查的策略展示。
- 绑定试卷、草稿未换卷、草稿换卷、草稿解绑时的前端请求省略语义。
- 管理端作答详情的人工批阅意见回显。

## 执行结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| PC 工作台目标测试 | `node --test tests/exam-workspace-navigation-and-paper-defaults.test.mjs` | 6/6 通过 |
| PC 类型检查 | `pnpm typecheck` | 通过 |
| PC 受影响文件 lint | `pnpm exec eslint ... --max-warnings=0` | 通过 |
| 后端批阅回归 | `mvn -pl sts-service-admin -am test -Dtest=ExamAttemptServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` | 19/19 通过 |
| 差异检查 | `git diff --check` | 通过 |

## 结论

前端已按 `docs/api/exam.openapi.json` 消费创建与草稿更新的试卷规则继承语义；未新增或修改接口、权限、数据库结构。批阅意见通过既有人工批阅接口保存，并在管理端作答详情回显。
