# 课程考核与独立考试来源分离 QA

## 验收范围

- 课程章节直接引用题库或已发布试卷版本，不再引用“已发布考试”。
- `AssessmentExecution` 统一承载独立考试、课程测验和课程题库练习的执行快照与来源归属。
- 课程题库不依赖试卷版本即可开考、保存、交卷、判分并完成一轮。
- 独立考试与课程测验分开查询、配置阅卷人和处理成绩；课程结果自动生效，独立考试继续人工发布。
- init 模块化完整 SQL 与 Admin Versioned Migration 建立相同最终结构。

## 关键场景结果

| 场景                        | 结果 | 证据                                                                         |
|---------------------------|----|----------------------------------------------------------------------------|
| 课程题库未绑定试卷版本启动             | 通过 | `CourseAssessmentAttemptServiceTest`、`ExamAttemptServiceImplTest`          |
| 同一资源在课程与独立考试中来源隔离         | 通过 | `AssessmentExecutionServiceImplTest`、`ExamMapperIT`、`ExamTenantBoundaryIT` |
| 课程客观题测验交卷后自动生效            | 通过 | `ExamAttemptServiceImplTest`                                               |
| 课程最后一道主观题批阅后自动生效          | 通过 | `ExamAttemptServiceImplTest`                                               |
| 主观题课程测验未配置阅卷人时禁止发布开课      | 通过 | `AssessmentReviewerServiceTest`、`CourseOfferingServiceImplTest`            |
| 当前教师未被分配时禁止批阅             | 通过 | `ExamAttemptServiceImplTest`                                               |
| 课程待阅任务不会进入独立考试批阅列表        | 通过 | `ExamAttemptQueryMapperIT`                                                 |
| 重复结果事实不重复落库               | 通过 | `CourseAssessmentResultMapperIT` 唯一约束                                      |
| 旧归属升级为执行归属                | 通过 | `FlywayMigrationIT` 覆盖 Candidate、Attempt、Reviewer 数据映射                     |
| 空库执行最新完整 SQL 并登记 baseline | 通过 | `AdminInitFlywayIT`                                                        |

## 实际验证

- `mvn -pl sts-service-admin -am test`：687 个用例，0 失败、0 错误、1 跳过。
-
`mvn -pl sts-service-admin -am -Pintegration-test -Dtest=CourseAssessmentResultMapperIT,CourseItemAssessmentResourceMapperIT,ExamAttemptQueryMapperIT,ExamReviewerAndCandidateQueryIT,ExamMapperIT,ExamTenantBoundaryIT,FlywayMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test`
：18 个用例通过。
-
`mvn -pl sts-service-admin -am -Pintegration-test -Dtest=ExamAttemptQueryMapperIT,FlywayMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test`
：5 个用例通过，包含最终来源过滤与升级数据回归。
- `mvn -pl sts-service-admin-init -am -Pintegration-test test`：1 个用例通过。
- `jq -e . docs/api/course.openapi.json`、`jq -e . docs/api/exam.openapi.json`：通过。
- OpenAPI 本地 `$ref` 完整性检查：通过。
- `openspec validate 2026-08-25-separate-course-assessment-from-exam --strict`：通过。
- `git diff --check`：通过。

## 结论

后端 Task 1～3 的验收标准均已覆盖，规格符合性与代码质量 Review 结论均为通过。前端不在 `lims` 本次交付范围内，以
`docs/api/*.openapi.json` 为后续实现契约。
