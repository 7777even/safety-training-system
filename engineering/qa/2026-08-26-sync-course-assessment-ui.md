# 课程考核与独立考试前端同步 QA

日期：2026-08-26  
OpenSpec Change：`2026-08-26-sync-course-assessment-ui`

## 验收范围

- 课程内容仅关联题库或已发布试卷版本，课程作答使用课程域 `/attempts`。
- 开课内课程测验可配置阅卷人、提交单题评分并查看自动生效结果。
- PC 与移动学员课程页可进入题库练习和课程测验；独立考试任务不再携带课程上下文。

## 自动化验证

| 命令 | 结果 |
| --- | --- |
| `pnpm --dir frontend/pc-web typecheck` | 通过 |
| `pnpm --dir frontend/mobile-app typecheck` | 通过 |
| `pnpm --dir frontend/pc-web exec node --test tests/course-exam-item-api.test.mjs` | 4/4 通过 |
| PC/移动受影响文件 ESLint | 通过 |
| `git diff --check` | 通过 |

## 结论

规格符合性 Review：通过。课程与独立考试的入口、请求字段和结果语义已隔离；没有残留课程旧 `/exam-attempts`、独立考试 `contextType/contextId` 或课程内容 `examId` 调用。

代码质量 Review：通过。变更限定在课程/考试类型、API、Mock、页面与契约读取测试；未引入依赖或后端改动。

真实后端联调未在本地执行，原因是当前无可用非生产环境和多角色测试账号；该项保留为部署前联调检查，未影响本次前端 Contract 验收结论。

## 发布前阅卷人配置回归

- 发现：草稿开课尚未生成课程考核执行对象时，批阅任务与结果读取会返回 `7062`，原前端并发读取导致“考核管理”面板整体加载失败。
- 修复：草稿阶段保留阅卷人配置入口，并提示先保存阅卷人；保存请求会由后端创建执行对象，随后可正常读取批阅任务和结果。
