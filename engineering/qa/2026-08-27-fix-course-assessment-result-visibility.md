# 课程考核学员作答详情可见性 QA

日期：2026-08-27  
OpenSpec Change：`2026-08-27-fix-course-assessment-result-visibility`  
Owner：`lims`

## 验收范围

- 课程题库练习和课程测验学员详情按 `RESULT_EFFECTIVE` 返回 `EFFECTIVE` 及成绩、标准答案、题目得分、解析。
- 待批阅仍隐藏成绩和标准答案，保留本人已提交答案。
- 独立考试学员读取的 `PUBLISHED` / `answerDetailMode` 行为不变。

## 自动化验证

| 命令                                                                                                             | 结果     |
|----------------------------------------------------------------------------------------------------------------|--------|
| `mvn -pl sts-service-admin -am test -Dtest=ExamLearnerServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` | 7/7 通过 |
| `python3 -c "import json; json.load(open('docs/api/course.openapi.json'))"`                                    | 通过     |
| `git diff --check`                                                                                             | 通过     |

未运行：Admin 全量零 DB、带 DB IT（无 schema/SQL 变化）。

## Review

- 规格符合性：通过。课程详情不再套用独立考试 `PUBLISHED + scorePublishedTime`；缺少 `examId` 不再挡住已生效答案详情。
- 代码质量：通过。仅 `ExamLearnerServiceImpl` 按来源分支，独立考试既有测试保持通过；无数据库、权限码或无关重构。

## 结论

后端目标验证通过。前端以更新后的 `docs/api/course.openapi.json` 另开；真实环境回看待部署后验证。
