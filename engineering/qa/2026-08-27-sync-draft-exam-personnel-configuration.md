# QA：草稿考试人员配置前端同步

日期：2026-08-27

## 验收范围

- 草稿考试阅卷人空数组按正常空态处理。
- 人员配置允许提交空 `reviewerUserIds` 清空阅卷人。
- Mock 与后端 OpenAPI 语义一致，发布后人员关系仍可读取。
- 课程考核阅卷人未改动。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| `pnpm --dir frontend/pc-web exec node --test --test-name-pattern="考试详情策略、批阅人和候选人分页均可从 Mock 回读|草稿考试 Mock 允许以空数组清空批阅人|草稿人员配置允许保存空阅卷人数组" tests/exam-contract-readback.test.mjs tests/exam-workspace-navigation-and-paper-defaults.test.mjs` | 通过，3/3 |
| `pnpm --dir frontend/pc-web typecheck` | 通过 |
| 目标文件 `git diff --check` | 通过；仅有既有 LF/CRLF 转换提示 |

## 已知基线问题

完整运行上述两个测试文件时共 15 条，其中本 Change 覆盖的 3 条均通过；另有 1 条既有断言失败：
`绑定试卷时按新契约省略未覆盖规则，草稿可换卷或解绑` 仍期待此前工作台规则调整已移除的“默认考试时长”文案。
该断言与草稿人员配置、阅卷人空数组和 Mock 无关，本 Change 未修改试卷规则实现或该既有断言。

## 待真实环境验收

在非生产环境验证：创建草稿后进入人员配置，确认 `GET reviewers` 返回空数组时页面正常加载；保存空批阅人后请求携带 `reviewerUserIds: []`；配置人员、修改草稿并发布后，人员仍可回显且学员端不展示未发布考试。
