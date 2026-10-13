# QA：课程考试绑定与成绩回接

日期：2026-07-29  
范围：`2026-07-29-link-course-items-to-exam-results`，仅后端；前端以 OpenAPI 为准另开。

## 已验证

- 课程内容项创建/更新：考试发布、启用、类型和 EXAM USE 授权校验；素材与考试引用互斥。
- 课程开考：从报名和冻结内容项生成 COURSE Candidate，不接受客户端伪造的课程上下文。
- 任务读取：考试项返回 `NOT_STARTED / IN_PROGRESS / WAITING_RESULT / RESULT_PUBLISHED`、成绩发布状态和按考试规则裁剪的正式成绩。
- 成绩发布：考试域以 `CourseExamResultPublished` 应用内事实回接课程学习域；仅正式通过结果完成对应课程内容项，重算报名完成态；非课程
  Candidate 不触发课程回接，重复发布不倒退进度。
- Candidate 唯一性：migration `V20260729153000` 将非课程字段归一为 0，以报名与内容项加入唯一键，避免同一报名内同考试不同内容项串链。

## 命令与结果

```text
mvn -pl sts-service-admin -am -Dtest=CourseItemServiceImplTest,CourseLearningServiceImplTest,CourseLearnerReadingServiceImplTest,CourseExamExecutionServiceTest,CourseLearningControllerWebSliceTest,ExamAttemptServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：30 tests，0 failures，0 errors。

```text
jq empty docs/api/course.openapi.json
jq empty docs/api/exam.openapi.json
git diff --check
openspec validate 2026-07-29-link-course-items-to-exam-results --strict
```

结果：全部通过。

## 未完成验证

```text
mvn -pl sts-service-admin -am -Pintegration-test -Dit.test=ExamMapperIT test
```

未执行完成：环境不存在 `/var/run/docker.sock`，Testcontainers 无法启动 MySQL；Maven 随后加载的 IT 共同失败。待 Docker
可用后重跑，重点验证 `ExamMapperIT.courseCandidateUniqueByEnrollmentAndItem`。

## 双轴 Review

- 规格符合性：通过。实现与已确认的类型映射、课程上下文边界、成绩可见性和完成口径一致；OpenAPI 已同步。
- 代码质量：通过（条件性）。已修复 Candidate 查询串链风险、批量读取考试展示字段和资产/考试互斥写入。真实 MySQL
  唯一键验证因环境阻塞，恢复环境后必须补跑 IT 才可关闭 Change。
