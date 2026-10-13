# 开课配置与课程测验阅卷流程 QA

日期：2026-08-26  
OpenSpec Change：`2026-08-26-fix-course-offering-assessment-configuration`

## 回归场景

1. 草稿开课打开考核管理时，仅读取和配置阅卷人，不读取批阅任务或结果。
2. 课程考核结果分页接口携带 `pageNum/pageSize` 时，不实例化 `Void`，正常委托 Service。
3. 新增/编辑开课在同一弹窗中保存草稿、配置课程测验阅卷人并发布。
4. 列表直接发布遇到 `5068` 时，自动引导至考核管理，而非留下无操作指引的错误。
5. 两项含主观题课程测验中任一项未配置阅卷人时，发布校验必须拒绝；考核管理按章节平铺显示各项测验与阅卷人姓名。
6. 课程测验人员选择器确认后立即保存；同一试卷仅展示版本号最大的已发布版本。
7. 课程题库练习与课程测验从课程目录开始、暂存、交卷和查看结果时，全程使用课程报名与内容项路径，不进入独立考试答题页。
8. 不可下载附件显示中文业务原因；课程结果生效后显示成绩、标准答案和解析。
9. 课程测验提交后将 `PENDING_REVIEW` 收敛为 `WAITING_RESULT`，保留已交卷作答 ID；再次进入不创建第二份答卷。
10. 课程结果自动生效后读取本人真实暂存答案、标准答案和解析；作答页提交或返回前自动暂存未保存答案，并在页头显示课程内容项名称。

## 自动化验证

| 命令 | 结果 |
| --- | --- |
| `mvn -pl sts-service-admin -am -Dtest=AssessmentReviewerServiceTest,CourseAssessmentManagementControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 6/6 通过 |
| `pnpm --dir frontend/pc-web exec node --test tests/course-exam-item-api.test.mjs` | 4/4 通过 |
| `pnpm --dir frontend/pc-web typecheck` | 通过 |
| PC 受影响组件 ESLint | 通过 |
| `git diff --check` | 通过 |
| `mvn -pl sts-service-admin -am -Dtest=CourseAssessmentAttemptServiceTest,CourseLearningControllerWebSliceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 12/12 通过 |
| `pnpm --dir frontend/pc-web exec node --test tests/learner-course-learning.test.mjs` | 11/11 通过 |
| `mvn -pl sts-service-admin -am -Dtest=CourseLearningServiceImplTest,ExamLearnerServiceImplTest,ExamAttemptServiceImplTest,CourseAssessmentAttemptServiceTest,CourseLearningControllerWebSliceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 64/64 通过 |
| `pnpm --dir frontend/pc-web exec node --test tests/learner-course-learning.test.mjs` | 12/12 通过 |
| `pnpm --dir frontend/pc-web typecheck` | 通过 |

## Review

- 规格符合性：通过。草稿、阅卷人、发布、批阅和结果按开课状态分段；每一项含主观题课程测验均独立受 `5068` 校验。
- 代码质量：通过。后端以多执行对象回归测试锁定遍历校验；前端仅调整开课面板和考核管理的调用时机与布局，无无关重构。
- Task 5 规格符合性与代码质量：通过。阅卷人保存仍复用既有替换接口；试卷版本按既有版本列表契约在前端收敛，不新增接口或字段。

真实环境联调仍需使用含主观题课程测验的非生产开课执行一次“保存草稿 → 保存阅卷人 → 发布开课”链路。

另需在非生产环境以学员身份完成一次“开始题库练习 → 暂存 → 手动交卷 → 查看课程成绩概览”联调，确认已部署后端包含课程域暂存与交卷接口。

Task 8 规格符合性与代码质量：通过。课程测验终态从服务端阻断重复创建，任务状态不再泄漏底层 `PENDING_REVIEW`；课程结果仅对课程自动生效状态放开答案和解析，独立考试发布策略保持不变。历史重复答卷不在本次缺陷修复中删除，学员任务优先展示已生效结果。
