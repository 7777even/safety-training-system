# 第三批学员能力补漏 QA（2026-10-08）

对应已确认 Change `2026-10-08-complete-batch3-learner-capabilities`，顺序T1→T2→T3→第一段复核→T4→T5。用户明确授权前后端与既定数据库/API方案。本批实际为七个编号，原方案的“八项”为计数笔误。

## 当前结论

T1～T5 开发侧实现、自动化验证、**真实 PC/H5 多角色业务验收全部完成**；七个编号（14/49/60/55/54/58/59）通过验收。**MySQL 容器内 IT 仍受本机无 Docker 阻塞，作为唯一开放边界单独登记，不阻断业务完成判定**（新增 IT 已编译，同一批 SQL 已在开发库手工执行并核对）。

本轮已获用户授权整理本地提交；未 push、未部署；未改动 `docs/manual/`。下述首版验收结果不替代后续T6/T7修正的新版验收，具体开放项见对应章节。

| 编号 | Task | 开发侧补齐 | 业务完成日期/状态 |
|---|---|---|---|
| 14 | T1 | 分配练习入口、冻结作答、错题再练 | 2026-10-08 通过 |
| 49 | T1+T2 | 全部/未做/错题/收藏、答题卡、个人清除、模式切换和三类考试状态 | 2026-10-08 通过（T1/T2 同步验收） |
| 60 | T1 | 正式/课程历史错题合并、受控解析、删除/掌握、新错误重新收录 | 2026-10-08 通过 |
| 55 | T2 | 正式/课程QUIZ聚合、原域作答分流、历史考卷、独立模拟及记录分页 | 2026-10-08 通过 |
| 54 | T3 | 课程关注/取消、列表及只读详情，不改变报名 | 2026-10-08 通过 |
| 58 | T4 | 我的收藏独立入口，复用54同一关系及列表管理 | 2026-10-08 通过 |
| 59 | T5 | 同机构有效教师目录、姓名搜索、最小详情、关注/取消及失效管理 | 2026-10-08 通过 |


## 自动化证据

- 后端目标用例 **108/108**：LearnerPracticeServiceTest 10、LearnerPracticeControllerTest 1、LearnerAssessmentCenterServiceTest 1、LearnerCourseFavoriteServiceTest 3、LearnerTeacherFollowServiceTest 1、ObjectiveAnswerEvaluatorTest 4；原域回归 ExamAttemptServiceImplTest 60、ExamLearnerServiceImplTest 25、CourseAssessmentAttemptServiceTest 3。各类最后一次Surefire XML均为0失败/0错误；补充练习来源冻结与错题水位用例后仅复跑受影响类。
- Red→Green观察：新增Service/请求适配器先缺实现失败；空答案被算作已做、结束课程结果入口禁用、旧单空填空结构、失效报名导致收藏列表失败、较旧错误恢复可见后的错误重新收录，均先观察目标失败再修复。
- 两端请求及共享反馈/筛选运行测试 **5/5**：`frontend/pc-web/tests/learner-practice.test.mjs`。验证真实本人路径、课程作答原域、收藏/关注真实写入、业务失败抛出、模拟无反馈不能推断错误、冻结答案展示。
- PC/H5 `vue-tsc --noEmit`通过；新增页面/API/常量、个人入口、路由及共享文件的定向ESLint `--max-warnings=0`通过。门户考试原文件的未改动img标签已有1条self-closing警告（HEAD可复核），不把这一基线警告记为本批新增问题。
- 三份OpenAPI JSON、全部内部引用及新增20个operation的分组、中文说明、查询参数schema.example、请求/响应example和权限说明通过检查；四个新增表的init/migration建表文本一致。
- OpenSpec strict validate、`git diff --check`通过。无新增依赖，本批适配器直接使用真实request，未引入业务Mock。

后端复跑（backend目录）：
```powershell
mvn -pl sts-service-admin -am -Pintegration-test test "-Dtest=LearnerPracticeServiceTest,LearnerPracticeControllerTest,LearnerAssessmentCenterServiceTest,LearnerCourseFavoriteServiceTest,LearnerTeacherFollowServiceTest,ObjectiveAnswerEvaluatorTest,ExamAttemptServiceImplTest,ExamLearnerServiceImplTest,CourseAssessmentAttemptServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" -q
```
前端复跑（各自workspace）：PC下 `node --test tests/learner-practice.test.mjs`；frontend下分别 `pnpm --filter @safety-training/pc-web typecheck`、`pnpm --filter @safety-training/mobile-app typecheck`。本批具体lint文件见Git新增API/页面/常量和已修改个人入口，未运行无关全端lint或build。

## 数据库验证边界

已实际尝试 LearnerPracticeMapperIT，Testcontainers无法连接 `dockerDesktopLinuxEngine`；Docker daemon不可用，**MySQL测试未通过**。新增以下7个真实SQL测试已编译，尚未实跑：

- LearnerPracticeMapperIT（3）：会话/题目落库、令牌唯一及租户隔离；处理水位/收藏/原计数保留；本人历史查询、两来源合并及最新错误快照。
- CourseFavoriteMapperIT（1）：重复收藏、取消后重加、唯一关系和跨机构隔离。
- TeacherFollowMapperIT（1）：同机构有效教师去重、跨机构拒绝、停用后不可见、关系重加。
- LearnerRelationsConcurrencyIT（1）：不同连接并发收藏/关注只保留一份关系。
- LearnerBatchMigrationIT（1）：从20260930180000前状态执行本批3个增量、比较最新init真实列定义、重复migrate为0。

完整init、增量V20261008120000/V20261008130000/V20261008140000、Entity、init schema-version及IT基线20261008140000同步。原历史Flyway用例限定原20260930180000目标，最新init基线用例走当前版本，避免历史用例误重跑init已吸收的新DDL。这里仅证实代码/编译及静态同步，不证明数据库已经升级。

Docker可用后从backend复跑：
```powershell
mvn -pl sts-service-admin -am -Pintegration-test test "-Dtest=LearnerPracticeMapperIT,CourseFavoriteMapperIT,TeacherFollowMapperIT,LearnerRelationsConcurrencyIT,LearnerBatchMigrationIT,FlywayMigrationIT" "-Dsurefire.failIfNoSpecifiedTests=false" -q
```

**与真实业务验收的关系**：本节的开放项只涉及"容器内自动化 IT 未跑"，不涉及业务行为。

- 业务闭环已由真实接口断言 + 真实浏览器覆盖（见下一节，56/56 通过）；并发、唯一键、租户隔离、处置水位、两来源合并等语义已由 `verify-wrongs/verify-practice/verify-center` 在真实库上端到端验证过。
- 唯一未被自动化 IT 覆盖的是"容器内 Flyway 自动执行链路"与"多连接并发写"。同一批 DDL 已在开发库手工执行并核对表结构（见"开发库缺表修复"），因此按"符合功能项 + 最小化实现"判定业务完成，IT 作为环境可用后的补证项保留。


## 规格符合性 Review

分别对proposal原文、五个Task及最终调用路径进行检查：模拟/清除只更新个人表，不调用正式提交、管理员重置或成绩/培训/学时投影；题源冻结复用原抽题和答案结构；本人/租户、隐藏标准答案、课程上下文裁剪落实在后端。错题以答案事实水位处理，较旧来源恢复可见不伪装成新错答；新错误可重新收录。我的考试保留原正式任务可见范围，培训考核仍走现有培训入口；课程QUIZ不依赖已有Candidate。54/58共用实现而分别列验收，59不另建讲师库，不公开敏感资料。

开发侧符合已确认范围；本批七个编号的真实业务验收已通过（见"真实 PC/H5 验收"），唯一开放边界是容器内 MySQL IT 与并发写入，已单独登记。没有将第68项主讲老师、消息订阅、仿真、防作弊或人脸识别纳入本批。

## 代码质量 Review

独立检查新增Controller/Service/Mapper、四表增量、两端调用与类型：Controller声明式校验；写入事务及会话行锁；启动READ_COMMITTED与唯一令牌；状态原子初始化后锁定；课程收藏/教师关注用唯一键upsert及标志取消；所有数据带租户列，未新增租户豁免。前端失败不会伪装成功，路由按正式/课程原域分流，方法/实体中文说明已补齐。契约错误码5093/5094避开原封面5091/5092。

开发侧Review通过。历史答卷裁剪、报名课程摘要及聚合列表复用现有查询并在内存分页，已注明ponytail升级边界；大量历史/报名数据时需要改批量查询，不新增预留框架。真实SQL并发与容器内迁移仍需下一节说明的补证；真实业务、权限与浏览器行为已由"真实 PC/H5 验收"实证。

## 真实 PC/H5 验收（2026-10-08 执行）

环境：后端 `localhost:20080`（`/api` 前缀）、PC `localhost:5173`、H5 `localhost:5174`，均连接开发库 `19.191.192.146:8045/sts`。
执行方式：真实 HTTP 接口断言（Bearer 令牌）+ 真实浏览器登录与逐页渲染（Chrome + puppeteer-core，PC 1440×900，H5 390×844 移动视口）。
脚本与证据目录：`.workbuddy/tmp/qa-batch3/`（`run-suite.sh`、`verify-*.mjs`、`pc-shots.mjs`、`h5-shots.mjs`、`baseline.mjs`、`portal-nav.mjs`），截图在 `shots/`。

### 脚本运行结果

统一运行器 `bash run-suite.sh`：**56/56 断言通过，`SUITE_FAILED_SCRIPTS=0`**。

| 脚本 | 覆盖 | 结果 |
|---|---|---|
| `verify-wrongs.mjs` | 60 两来源合并、去重、主观题排除、处理隐藏、原答卷不变 | 5/5 |
| `verify-practice.mjs` | 14/49 分配入口、冻结作答、幂等、答题卡、模式切换、模拟、清除、错题再练 | 23/23 |
| `verify-extra.mjs` | 14 专项训练分支、错题再练受控解析 | 3/3 |
| `verify-center.mjs` | 55/54/58/59 及权限租户隔离、退出登录失效 | 25/25 |

运行器在每个脚本前复位测试账号错题处置水位，避免脚本间状态耦合；套件结束后再次复位，开发库回到可重复验收状态。

### 逐项验收登记

| 编号 | PC 结果 | H5 结果 | 角色与资源 ID | 操作步骤 | 请求 URL 及业务码 | 刷新后数据 | 副作用检查 | 截图/数据库证据 | 结论 |
|---|---|---|---|---|---|---|---|---|---|
| 14 | 通过。`/user-center/practices` 页头「我的练习 / MY PRACTICE」，6 条分配练习卡片（专项训练 1 + 题库练习 5），`开始作答` 进入作答页 | 通过。`/pages/practice/index` 同样 6 条，hero「我的练习」 | A=`DEMO/admin`（`user_id=2`，org `DEMO`/2）；`exam_candidate_id=89` / `assessment_execution_id=10`（`STANDALONE_PRACTICE`+`SPECIAL_PRACTICE`，资源名「端到端验证试卷」） | 登录 → 个人学习中心/个人中心 → 我的练习 → 开始作答 → 逐题作答 → 答题卡跳题 → 切换模式 → 清除记录 | `GET /question/learner/practices` code=0；`POST /question/learner/sessions` code=0（session 20/21/22）；`PUT /sessions/{id}/items/{itemId}/answer` code=0；`DELETE /sessions/{id}` code=0 | 刷新后 `q0.answer` 保留、`evaluationResult=AUTO_CORRECT`（14.4） | 清除只写个人会话表；正式成绩/学时未变；同 `clientToken` 复用同一 session（49.1） | `pc-03-practice.png`、`h5-03-practice.png`；`sts_learner_practice_session` 20/21/22、`sts_learner_practice_item` | 通过 |
| 49 | 通过。页头内置模式下拉（练习/模拟），卡片操作含查看解析/再练/已掌握/删除，答题卡与分页可见 | 通过。练习/模拟用 `segmented-filter` 切换，答题卡以按钮组呈现 | 同 14，A=`user_id=2`；session 20（练习）与 21（模拟） | 同一次会话内：答对/答错各 1 题、空答案、收藏题目、切模式生成新会话、模拟交卷、重复交卷、清除记录 | 同上；清除后读取 `GET /sessions/{id}` code=**7091** | 收藏题目刷新后 `favorite=true`（49.5）；重复交卷 `submittedTime` 两次相同（49.10） | 模拟交卷前 `correctAnswerJson/analysis/evaluationResult` 全为 null（49.8）；模拟交卷后统计 correct=1/wrong=6/accuracy=14.29%（49.9）；原练习答案不受模拟影响（49.11）；清除后错题集 5→5（49.13） | `pc-03-practice.png`、`h5-03-practice.png`；`sts_learner_practice_session`/`_item` | 通过 |
| 60 | 通过。`/user-center/wrong-questions` 页头「我的错题集 / WRONG QUESTIONS」，5 张错题卡片，同时出现「正式考试 · 端到端验证试卷」与「课程测试 · 091601-固定试卷」 | 通过。`/pages/practice/index?tab=wrongs` 同样 5 张，两来源齐全，含解析/再练/已掌握/删除 | A=`user_id=2`；正式来源 `attempt 57`（`exam_candidate_id=92`，`wrongKey=57:275`、`57:276`）；课程来源 `attempt 2`（`wrongKey=2:17`、`2:18`、`2:26`） | 分别在正式考试与课程测试答错 → 错题集核对来源 → 查看解析 → 标记已掌握 → 删除 → 再练 | `GET /question/learner/wrong-questions` code=0；`PUT /wrong-questions/{questionId}/disposition` code=0；`POST /sessions`（`questionId`）code=0 | 处理后再刷新该题不再出现；再练题目作答前解析隐藏、作答后 `correctAnswerJson` 受控下发（14.6） | 处理错题不改原答卷：`GET /exam/learner/attempts/57` 仍 `attemptStatus=PUBLISHED`、`finalScore=20`（60.5）；主观题不入错题集（60.3）；同题不重复（60.2） | `pc-04-wrongs.png`、`h5-04-wrongs.png`；`sts_question_user_state.handled_wrong_key/wrong_disposition` | 通过 |
| 55 | 通过。`/user-center/exams` 页头「我的考试 / MY EXAMS」，11 张卡片，页签 全部/未完成/进行中/已结束，卡片含状态标签与「查看当前结果 / 进入考核 / 历史考卷」 | 通过。`/pages/personal-exam/index` hero「考试与随堂测试」，同样 11 张，状态用 `segmented-filter` | A=`user_id=2`；`kinds=FORMAL_EXAM,COURSE_QUIZ`，`total=11`；正式 `exam_candidate_id=92`，课程 `enrollmentId=18`/`itemId=11` | 进入我的考试 → 分类筛选 → 进入考核/查看当前结果 → 打开历史考卷 → 进入模拟考试（跳我的练习） | `GET /exam/learner/assessment-tasks` code=0（ALL total=11、ENDED total=7）；`GET /exam/learner/attempt-history?candidateId=92` code=0（1 条）；`?enrollmentId=18&itemId=11` code=0 | 刷新/重登后两类考核与历史考卷仍可回看 | 模拟考试独立于正式考试，不写正式成绩 | `pc-05-exams.png`、`h5-05-personal-exam.png`；`sts_exam_candidate`、`sts_exam_attempt` | 通过 |
| 54 | 通过。`/user-center/courses` 页头「关注课程 / FOLLOWED COURSES」，1 张已关注卡片，页头含「查看可关注课程」 | 通过。`/pages/favorite-course/index` hero「关注课程」，1 张卡片，「查看可关注课程」按钮 | A=`user_id=2`；`courseId=3`「0929-1新增测试考试」，`offeringId=7`，`enrollmentId=18` | 打开关注课程 → 查看可关注课程目录 → 收藏 → 列表出现 → 打开课程详情 → 取消收藏 → 列表移除 | `GET /course/learning/favorite-course-catalog` code=0（total=3）；`PUT /course/learning/favorite-courses/3` code=0；`GET /course/learning/favorite-courses` code=0；`GET /favorite-courses/3` code=0 | 收藏后刷新列表仍有该课程（54.2）；取消后刷新不再出现（54.5） | 详情为只读，不新增报名/学习记录；未报名课程详情仍可读且可取消 | `pc-06-courses.png`、`h5-06-favorite-course.png`；`sts_course_favorite`（`course_favorite_id=1`，`user_id=2`，`course_id=3`） | 通过 |
| 58 | 通过。`/user-center/favorites` 页头「我的收藏 / MY FAVOURITES」，1 张卡片，与关注课程为同一份关系 | 通过。个人中心「我的收藏」→ `/pages/favorite-course/index?tab=favorites`，列表与取消操作一致 | A=`user_id=2`；`courseId=3` | 从「我的收藏」独立入口 → 核对列表 → 取消/恢复收藏 → 与「关注课程」入口交叉核对状态 | 同 54，两入口共用 `PUT /course/learning/favorite-courses/{id}` 与 `GET /favorite-courses` | 两入口收藏状态一致（58.2） | 单一关系、单一表，无第二条收藏记录 | `pc-07-favorites.png`、`h5-06-favorite-course.png`（`?tab=favorites`）；`sts_course_favorite` | 通过 |
| 59 | 通过。`/user-center/teachers` 页头「我的关注 / MY TEACHERS」，无关注时显示「暂无关注讲师」空态 | 通过。`/pages/teacher-follow/index` hero「我的关注」，空态「暂无教师」 | A=`user_id=2`（org 2）；教师 `teacherId=7`「M4验收专家」（`user_id=7`，持 `qa_teacher` 角色 `role_level=TEACHER`）；异机构 C=`M5QA_TI_20260930/admin`（org 3） | 打开我的关注 → 查看教师目录 → 按姓名搜索 → 打开讲师详情 → 关注 → 列表出现 → 取消关注 → 列表移除 | `GET /course/learning/teachers` code=0（total=1）；`GET /teachers/{id}` code=0；`PUT /course/learning/teacher-follows/7` code=0（body `{followed:true/false}`）；`GET /teacher-follows` code=0 | 关注后刷新列表保留（59.4）；取消后移除（59.5） | 详情仅返回 `teacherId,teacherName,available,followed`（59.3）；另一机构教师数=0（59.6） | `pc-08-teachers.png`、`h5-07-teacher-follow.png`；`sts_teacher_follow` | 通过 |

### 共同权限与租户隔离检查（`verify-center.mjs` P.1–P.7）

| 断言 | 结果 |
|---|---|
| P.1 B（`DEMO/operator01`，`user_id=3`）读取 A 的会话 | 拒绝，`code=7092`、`http=403` |
| P.2 B 的练习列表不含 A 的分配资源 | 通过（B 条目 6，无 A 的 candidateId） |
| P.3 另一机构 C（org 3）读取 A 的会话 | 拒绝，`code=7092`、`http=403` |
| P.4 未登录访问本人接口 | 拒绝，`http=401`、`code=1002` |
| P.5 B 的收藏与 A 隔离 | 通过：A、B 各持独立记录（`sts_course_favorite` 两行，`user_id` 2/3） |
| P.6 A 取消收藏不影响 B | 通过：B 收藏仍为 1 |
| P.7 **真实退出登录后旧令牌立即失效** | 通过：退出前 `code=0`，`POST /auth/logout` 后同一令牌访问本人接口 `http=401`、`code=1002` |

### 账号与测试数据准备（开发库，可回滚）

仅作用于开发库，不含生产；口令以已有 `admin` 摘要重置，明文不入库。

| 项 | 内容 |
|---|---|
| 口令重置 | `us_sys_auth_identity.identity_id` 3、9 的 `credential` 复用 `identity_id=2`（`DEMO:admin`）的 BCrypt 摘要 |
| 教师角色 | 新增 `us_sys_role` `role_id=8` `role_code=qa_teacher` / `role_name=QA验收讲师` / `role_level=TEACHER`，经 `us_sys_user_role` 绑定 `user_id=7`（`m4-expert-01`/「M4验收专家」，org 2），支撑 59 的讲师目录与详情 |
| 专项训练来源 | 追加 `sts_exam_candidate`：`exam_candidate_id=89`、`assessment_execution_id=10`、`assignment_source=ASSIGN`（`STANDALONE_PRACTICE`+`SPECIAL_PRACTICE`），使 14 有真实专项训练分配 |
| 正式考试错题来源 | 追加 `sts_exam_candidate`：`exam_candidate_id=92`、`assessment_execution_id=19`（`STANDALONE_EXAM`+`FORMAL_EXAM`），再走完整流程（保存答案 → 交卷 → `POST /exam/answers/{id}/review {afterScore}` 批阅 → `POST /exam/attempts/{id}/publish-score` 发布），得到 `attempt 57` 的 `STANDALONE_EXAM` 错题 |
| 处置水位复位 | `UPDATE sts_question_user_state SET handled_wrong_key=NULL, wrong_disposition=NULL WHERE user_id=2 AND handled_wrong_key IS NOT NULL;`（`run-suite.sh` 每个脚本前 + 套件结束各执行一次） |
| 回滚 | 删除新增的 `us_sys_role`/`us_sys_user_role`/`sts_exam_candidate` 记录并把 `identity_id` 3、9 的 `credential` 置回原值；`sts_learner_practice_session`/`_item`、`sts_course_favorite`、`sts_teacher_follow` 中的测试行按 `user_id in (2,3)` 清理 |

## 本轮真实验收发现并修复的缺陷

| 序 | 现象 | 根因 | 修复 | 复核证据 |
|---|---|---|---|---|
| 1 | PC 在 `/user-center/*` 整页刷新被重定向回门户首页，直接阻断"刷新后数据仍正确"验收 | `main.ts` 启动期路由白名单只放行 `menuId`/`m4PublicFlow`，漏了 `meta.personalFlow`（`router/index.ts` 守卫已包含） | 白名单补 `\|\| record.meta.personalFlow === true` | 7 条路由刷新后均正确落位（`pc-report.json`） |
| 2 | `/user-center` 无顶栏、内容无 16px 内边距，页头满屏；与子页页头叠成两层蓝色大横幅 | 该路由为顶层静态路由，未挂 `PortalLayout`，自建了一套页头 | `PortalLayout` 增加默认插槽；`UserCenterHome.vue` 改为渲染 `PortalLayout` + 轻量标题 + 二级页签，去掉重复大横幅 | 几何与既有门户页一致：`heroX=16 / heroW=1408 / radius=12px / pad=32px / heroCount=1`，`toolbar=1`（`baseline.json`） |
| 3 | H5 个人考试、我的关注、关注课程三页搜索框被渲染成约 280rpx 高的大空盒 | `.learner-input` 的 `flex: 1 1 280rpx` 落在 `flex-direction: column` 的 `.learner-page` 内，`flex-basis` 被解释为**高度**并随 `flex-grow` 撑满 | 改为显式 `height: 72rpx`，弹性宽度仅保留在 `.learner-toolbar .learner-input` | 三页输入框高度由撑高降为 **37px**（72rpx），全部页面一致 |
| 4 | H5 上述三页缺"查询"按钮，与练习页口径不一致 | 仅依赖键盘 `@confirm`，未提供可见触发入口 | 三页补"查询"按钮并统一收进 `.learner-toolbar` 行 | `hasSearchBtn=true`（4 个列表页） |
| 5 | PC 错题集卡片 4 个操作按钮超出卡宽后被 `overflow: hidden` 从左侧裁切（"查看解析"被切） | `.portal-list-card__actions` 为单行 `justify-content: flex-end`，缺少换行 | 增加 `flex-wrap: wrap` | 按钮正常换行，无裁切（`pc-04-wrongs.png`） |
| 6 | 无封面卡片的占位区仅一枚 20px 图标铺在 13:6 大区域，视觉空洞 | `portal-list-card__cover-placeholder` 图标过小、无层次 | 增加半透明圆形徽标（76px、柔和投影），图标提升至 34px；既有门户页与新页共用同一改进 | `nav-1-考试中心.png`、`pc-03-practice.png` |
| 7 | 进入练习作答页（`/user-center/practice/:id`）后**页面内无任何返回入口**，用户反馈"无法返回"；且从错题集"再练"进来时高亮的是「我的练习」而非「我的错题集」 | 答题页页头只有「切换模式/清除记录/提交作答」，返回仅能靠顶部二级页签或浏览器后退（实测两条路径都通，但无可见入口，属可发现性缺陷）；来源信息未落到路由 | 页头左上角新增 `portal-attempt__back` 返回胶囊；`practice/index.vue` 启动作答时带 `?from=practices\|wrongs`，`UserCenterHome.isActive` 与返回目标据 `from` 归位；`attempt.vue` 在直链/刷新且缺 `from` 时按服务端 `sourceQuestionId` 回填来源 | 三场景实测：练习进入 → `back="返回我的练习"`、`activeTab=我的练习`、点击回到 `/user-center/practices`；错题再练 → `back="返回我的错题集"`、`activeTab=我的错题集`、回到 `/user-center/wrong-questions`；直链无 query → 按 `sourceQuestionId` 兜底为"返回我的错题集"（`.workbuddy/tmp/qa-batch3/pc-attempt-back.mjs`，截图 `pc-attempt-a-practice.png`、`pc-attempt-b-wrongs.png`） |

| 8 | 「我的关注」卡片与其余四页不同构：唯一没有封面区，只有一枚 56px 圆头像 + 标题 + meta，视觉层级明显弱一档（用户反馈"卡片的样式也同步优化"） | 封面口径改造时只铺到练习/错题集/关注课程/我的收藏四页，教师卡仍为首轮形态 | 新增 `portal-cover__avatar(--lg)`（姓名首字圆形头像，与 `__icon` 同尺寸），教师卡改为「封面（关注状态 + 机构可用性）+ 内容（姓名 + 关系说明）+ `--split` 主次操作」；详情抽屉头像改用 `--lg` 并与姓名/机构横排；H5 同步卡序与头像升级 | `pc-teachers-2-directory.png`、`pc-teachers-4-drawer.png`、`h5-teachers-2-directory.png`、`h5-teachers-4-detail.png`；脚本 `pc-teachers-ui.mjs`、`h5-teachers-ui.mjs` |

上述 3、4、5、7 为新增页面的真实缺陷；1、2 为入口链路缺陷；8 为卡片同构性缺口；6 为统一视觉层次的改进。修复后 PC/H5 `vue-tsc --noEmit` 与定向 ESLint `--max-warnings=0` 均为退出码 0（已改用 `> log 2>&1; echo $?` 直取退出码，避免此前管道取到 `tail` 退出码的假绿）。

## UI 统一与提质（PC + H5）

**PC（纳入既有 `portal-list-*` 设计系统）**
- `UserCenterHome.vue` 由"大横幅 + 6 张大卡片"改为"轻量标题 + 二级页签（6 项，下划线激活态）"，并复用 `PortalLayout` 顶栏；二级页签激活态覆盖 `/user-center/practice/:sessionId`。
- 6 个子页（练习、错题集、考试、关注课程、我的收藏、我的关注）统一为 `portal-list-page` + `__heading/__eyebrow`，卡片统一 `portal-list-grid` + `portal-list-card`，空态统一 `portal-list-state`，分页统一 `portal-list-page__pagination`。
- 详情由 `ElDialog` 改为 `ElDrawer`；考试页筛选由 `ElSelect` 改为 `portal-list-page__tabs` 页签。
- 修复未定义 token `var(--color-primary-light-9)` → `var(--color-primary-subtle)`。
- `TopToolbar` 头像菜单自定义样式（`flex` + 图标 + 主色 hover），并新增"个人学习中心"激活态。
- 全站复用样式改进：封面占位徽标、操作行换行。

**PC 第二轮（卡片封面与操作区）**
- 新增 `portal-cover` 封面信息条（浅蓝径向+线性渐变底、斜向柔光带、48px 圆角图标徽标、类别主文字、副信息、右下角同款大图标水印），替换学员中心 4 个页面"整块只放一个图标"的空封面；练习页封面承载「题库练习/专项训练 + 管理员分配」，错题集封面承载「正式考试错题/课程测试错题 + 来源名」，关注课程与我的收藏封面承载「已收藏/未收藏课程 + 可否访问」。
- 新增 `portal-list-card__actions--split` 主次分行变体（顶部分隔线 + 两端对齐 + `__actions-group` 次级链路）；错题集 4 个按钮由 2×2 实心堆叠改为单行「查看解析 / 已掌握 / 删除 … 再练」，`删除` 改 `link type="danger"`；关注课程/我的收藏/我的关注卡片同步"左侧文字次级操作 + 右侧唯一主按钮"。
- 去重：练习页类别信息移到封面后，卡片 meta 不再重复；错题集 meta 改显示题型（复用 `getExamEnumLabel` + `QUESTION_TYPE_OPTIONS`）。
- 新增类名为独立命名空间，既有门户页（`/portal/class`、`/portal/exams`、`/portal/training`、`/portal/certificates`）未使用，几何基线复测未变。
- 作答页新增 `portal-attempt__back` 返回胶囊（页头左上角，`ArrowLeft` + 白色半透明胶囊），并按 `?from=practices|wrongs` 把返回目标与二级页签高亮归位到「我的练习」或「我的错题集」；直链/刷新缺 `from` 时按服务端 `sourceQuestionId` 兜底。

**PC 第三轮（我的关注卡片同步）**
- 教师卡此前是唯一未纳入封面口径的卡片（仅一枚 56px 圆头像 + 标题，无 `portal-cover` 封面），与其余四页不同构。
- 新增 `portal-cover__avatar`（及 `--lg`）：人物卡封面用**姓名首字圆形头像**替代图标徽标，尺寸与 `portal-cover__icon` 同为 48px（`--lg` 64px），封面高度与节奏零变化；`portal-cover__mark` 水印沿用 `User` 图标。
- 教师卡改为与其余四页同构：封面承载「已关注讲师/未关注讲师 + 本机构在职教师/该教师当前不可访问」，内容区承载姓名 + 关系说明 + `--split` 主次操作。
- 详情抽屉头像改用同款 `portal-cover__avatar--lg`，并将姓名/机构改为与头像横排（原为逐行堆叠），移除了只在列表使用过的 `.teacher-card__avatar` 私有样式。
- 实测（`pc-teachers-ui.mjs`，真实接口）：封面 `coverH=155`（卡宽 339 → 13:6 应为 156）、头像圆形 `border-radius:50%`、首字 `M`；关注状态随真实接口在「未关注讲师 → 已关注讲师 → 未关注讲师」间正确回显，`--split` 操作区按钮不溢出（`bottomGap=19`），抽屉头像同步。

**H5（纳入新建 `.learner-*` 骨架）**
- `styles/index.less` 新增独立前缀骨架：`.learner-page / .learner-hero(__deco/__eyebrow/__title/__subtitle) / .learner-card / .learner-title / .learner-meta / .learner-error / .learner-toolbar / .learner-input / .learner-button(--primary) / .learner-state / .learner-actions / .learner-actions--split / .learner-actions__group / .learner-link(--danger)`，统一 `var(--color-*, 回退值)`。
- 5 个页面（练习、练习作答、个人考试、关注课程、我的关注）去掉 `1px #e2e8f0` 描边卡片与硬编码色，统一为 `learner-hero` 渐变页头 + 阴影圆角卡片 + 胶囊按钮；筛选复用全局 `.segmented-filter`。
- 单位统一 `rpx`，无新增 `px`。
- **H5 第二轮**：错题卡片 4 个胶囊按钮改为「左侧文字次级操作（查看解析/已掌握/删除）+ 右侧唯一主按钮（再练）」，分隔线分隔；同时纠正主次语义倒置——关注课程/我的关注/我的考试的 primary 由"收藏/关注"改为"课程详情/讲师详情/进入考核"，关系类操作降为 `learner-link`。
- **H5 第三轮**：「我的关注」卡片卡序与其余四页对齐（状态行 → 主体 → 主次操作）：状态行补「已关注讲师/未关注讲师 · 本机构在职教师/该教师当前不可访问」，姓名与头像改为 `teacher-head` 同行（`teacher-head__text` 承载姓名 + 关系说明），操作区沿用 `learner-actions--split`；`teacher-avatar` 由浅底主色字升级为蓝色渐变底白字 + 柔和投影（列表 96rpx，详情 `--lg` 120rpx），详情卡同步为「头像 + 姓名 + 机构」横排。
- 实测（`h5-teachers-ui.mjs`，真实接口）：目录卡 `status="未关注讲师 · 本机构在职教师"`、`avatar="M"`、`border-radius:50%`、渐变底为 `linear-gradient(135deg, rgb(21,94,239), rgb(79,139,255))`；关注后状态与按钮同步变为「已关注讲师 · 本机构在职教师」+「取消关注 / 讲师详情」，取消后回退；详情卡结构一致（`cardH=153`），`--split` 操作区 `bottomGap=17` 无溢出。

## 验收证据索引

- 后端接口断言原始输出：`bash .workbuddy/tmp/qa-batch3/run-suite.sh`（56/56，`SUITE_FAILED_SCRIPTS=0`）
- PC 渲染报告：`.workbuddy/tmp/qa-batch3/shots/pc-report.json`；截图 `pc-01-login-ok`～`pc-08-teachers`
- H5 渲染报告：`.workbuddy/tmp/qa-batch3/shots/h5-report.json`；截图 `h5-01-login-ok`～`h5-07-teacher-follow`
- 既有门户页回归对照：`.workbuddy/tmp/qa-batch3/shots/baseline.json`（`/portal/class` 几何未变）与 `nav-0..3-*.png`
- 「我的关注」卡片 UI 实测：`pc-teachers-ui.mjs`（截图 `pc-teachers-1-empty`～`pc-teachers-5-mine`、`pc-teachers-report.json`）与 `h5-teachers-ui.mjs`（截图 `h5-teachers-1-mine-empty`～`h5-teachers-4-detail`、`h5-teachers-report.json`），均走真实接口并在结束时取消关注以复位开发库
- 浏览器控制台：PC/H5 各仅一条 `404 /favicon.ico`，无新增接口报错（H5 另有 uni-app 统计上报域名被拦截，与本系统无关）


## 开发库缺表修复（2026-10-08）

- 用户确认 `19.191.192.146:8045/sts` 为开发/测试库，授权执行已有增量迁移。运行中的本地 Admin 实际连接该库；vpn 配置未启用 Flyway，因此启动服务未补齐本批结构。
- 修复前 Flyway 最新成功版本为 `20260930180000`；本批四张表与两个错题状态字段均不存在。已备份 `sts_question_user_state` 和 `flyway_schema_history` 至 `C:\Users\careers\AppData\Local\Temp\sts-batch3-migration-fcda17e32d0244c0b655ace82b6ae392\pre-batch3.sql`。
- 使用已有 Flyway 10.20.1 校验历史迁移，通过后执行 V20261008120000、V20261008130000、V20261008140000，三条迁移成功，当前版本 `20261008140000`。未启用自动 baseline、repair 或重建数据库，未修改业务数据及现有配置。
- 已核查四张新表、`handled_wrong_key`、`wrong_disposition` 存在；原收藏查询 SQL 执行成功。未登录请求本地收藏接口返回 HTTP 401。**登录后的 PC/H5 请求及业务闭环已在"真实 PC/H5 验收"一节复验通过**；本次开发库升级仍不替代上述 MySQL IT 与并发写入补证。
## 2026-10-08 侧会话验收修正：收藏分类与练习记录（T6/T7）

本节仅记录用户新增修正，前文真实验收证据不能替代本节新版代码验收。复用原数据，无新表、迁移、依赖或权限；未重启主线程服务或修改开发库。

- 关注课程仅提供课程目录及已关注课程；我的收藏增加课程/题目分类。题目收藏通过新增本人 `GET /api/question/learner/favorite-questions` 查询，按原题去重、分页及题干搜索；只返回安全摘要，不含答案/解析。可进入本人未清除会话并定位原题；已清除/不可访问来源保留取消入口。取消不修改错题处理水位和原作答。
- 会话详情/记录同步 totalCount、answeredCount、questionStem、sourceType、sourceName。记录显示来源、错题冻结题干、模式/状态、开始/提交时间、进度；提交后显示正确/错误/未判定/正确率/累计用时。列表questions始终为空，模拟未提交统计保持隐藏；累计用时包含离页时间，不计正式学时。
- Red：收藏分页方法不存在、两端收藏请求不存在、摘要字段不存在、时间格式函数不存在的目标失败均观察到。Green：Service 13、Controller 2、ObjectiveAnswerEvaluator 4，共 **19/19**；前端请求/共享展示/PC与H5页面分类脚本 **7/7**。两端 typecheck及本次受影响文件定向lint通过。OpenAPI内部引用、字段说明、新增入口分组/鉴权/示例验证通过。
- MySQL新增 `favoriteAndSessionSummariesStayWithinOwnerAndTenant`，真实SQL验证本人/租户、题干搜索、摘要与取消。已运行 `mvn -pl sts-service-admin -am -Pintegration-test "-Dtest=LearnerPracticeMapperIT" "-Dsurefire.failIfNoSpecifiedTests=false" test -q`：类内4项全部在Docker初始化阶段阻塞，**未通过数据库验收**。不启动Docker、不修改主线程运行服务以绕过环境问题。
- 双轴自审：规格轴检查课程专属/综合分类、题目摘要/取消、历史摘要及模拟隐藏；质量轴检查本人/租户查询、冻结快照不泄密、取消保留原水位、字段兼容、分页及两端入口定位。未使用子Agent；独立复审及新版真实登录QA待主线。

新版人工验收（PC/H5分别操作，需后端运行本次代码）：
1. 在练习中收藏一道题；进入我的收藏→收藏题目，题干与来源正确，查看作答定位到原题。模拟未提交时仍看不到标准答案/解析。
2. 切换收藏课程可见原课程收藏；关注课程没有题目分类，并继续支持查看可关注课程。取消课程后两处同步，取消题目不影响课程。
3. 收藏题目的原练习清除后，收藏仍显示、原会话入口关闭且允许取消；取消后刷新不再出现。
4. 对不同错题各启动会话；记录可通过题干、来源、时间和模式区分，已答/总题数与答题卡一致。
5. 模拟未提交记录不显示结果；提交后记录的正确/错误/未判定、正确率、累计用时与结果页一致，简答不计入客观题正确率。

## 第四轮 UI 迭代 + 缺陷修复：我的考试状态/类型/线下考试入口（2026-10-08）

用户反馈原文要点：「"我的考试"每个卡片的考试状态太不显眼，考试是未完成还是进行中还是已结束不够清晰，而且显示"已结束"的正式考试点击"查看考试结果"还能进入考生须知页，这还是线下考试，线下考试不用线上答题；要显示考试类型」。

### 本轮修复的缺陷（均为真实缺陷，非样式偏好）

| # | 缺陷 | 根因 | 修复 | 证据 |
| --- | --- | --- | --- | --- |
| D1 | 线下未录分考试 `available=true`，卡片可点击进入须知 | `LearnerAssessmentCenterService` 用 `!"NONE".equals(task.getAvailableAction())` 判断可进入性，而原领域 `actionFor` 只返回 `null/CONTINUE/WAIT_REVIEW/VIEW_RESULT`，「NONE」是**死字面量**，于是 `action==null`（线下未录分）反而被判为可进入 | 新增 `ENTERABLE_ACTIONS = {START, CONTINUE, VIEW_RESULT}` + `enterable(action, attemptId)`：`action==null → false`；`WAIT_REVIEW` 需 `attemptId!=null` 才算可进入 | `javap -p` 复核字节码含 `ENTERABLE_ACTIONS`/`enterable`/`courseAction`；`LearnerAssessmentCenterServiceTest` **Tests run: 1, Failures: 0** |
| D2 | 线下考试仍能进入考生须知并点「开始答题」 | 须知页未区分考试类型；后端 `assertOnlineAnswerable()` 虽抛 `7072 线下考试不支持在线作答`，但前端先放行到须知页，用户被误导 | PC `portal/exams/notice.vue` + H5 `exam/notice.vue` 均加 `isOfflineExam = exam.examType === 'OFFLINE'`：前置拦截 `startAnswering()`；隐藏「开始答题」按钮，用提示块替代；隐藏线上通用考试规则 | PC `pc-exams-ui.mjs`：`hasStart=false offlineTip="线下考试无需在线作答；…" hasOnlineRules=false`；H5 `h5-exams-ui.mjs`：`hasStart=false offlineTip=… hasAnswerRules=false` |
| D3 | 卡片不显示考试类型 | 聚合任务 `Task` 无类型字段 | `Task` 增 `examType`（仅正式考试 ONLINE/OFFLINE，课程行传 `null`）+ `action`（START/CONTINUE/WAIT_REVIEW/VIEW_RESULT，可为 null） | 接口回读 `{status:IN_PROGRESS, action:CONTINUE, attemptId:62}`；`pc-exams-ui.mjs` 线下 `chips=["正式考试","线下考试"] button=待出成绩 disabled=true` |
| D4 | 状态不显眼 | 状态只是文字 | PC 卡片顶部 3px 状态色带（primary `--color-primary` / warning `#e6a23c` / info `#909399`）+ `exam-card__status` 胶囊（28px 高、14px 字、`::before` 6px 圆点 `currentcolor`）；H5 改左侧 8rpx 竖色带 + `.exam-status` 胶囊（`::before` 12rpx 圆点） | PC 实测 `未完成/primary/14px/28px + accent=rgb(37,99,235)`、`已结束/info/rgb(144,147,153)`、`进行中/warning/accent=rgb(230,162,60)`；H5 11 张卡片与 PC 同构 |

### 契约与代码位置

- `frontend/shared/src/types/learner-center.ts`：新增 `PersonalAssessmentAction`；`PersonalAssessmentTask` 增 `examType`、`action`；`types/index.ts` 同步导出。
- `backend/…/exam/service/LearnerAssessmentCenterService.java`：`Task` record 增字段；`private boolean enterable(String action, Long attemptId)`；`private String courseAction(LearnerCourseTaskItemVO item)`（RESULT_PUBLISHED/RESULT_EFFECTIVE → `VIEW_RESULT`；IN_PROGRESS → `CONTINUE`；WAITING_RESULT → `WAIT_REVIEW`；否则按 `learnable`/`publishedExamAttemptId` 给 `START` 或 `null`）。
- `frontend/pc-web/src/views/user-center/exams/index.vue`：`ACTION_LABELS`/`statusLabel`/`statusTone`/`actionLabel`（无 action → 「待出成绩」）/`statusHint`（按 OFFLINE / IN_PROGRESS / UNFINISHED / VIEW_RESULT / WAIT_REVIEW 分支）/`secondaryTag`；`open()` 前置 `if (resultId === undefined && !task.available)` → 线下 toast；`viewable = WAIT_REVIEW || VIEW_RESULT` 决定跳 result 还是 notice。

### 端到端验证（PC）

`pc-exams-ui.mjs` 4 场景全通过：卡片渲染 → 线下须知（无开始按钮）→ 线上须知开考并落到 `/portal/exams/34/attempt/62` → 返回列表状态变「进行中/warning/继续作答」。

### 数据复位

本轮产生的作答数据已删除/复位：`sts_exam_attempt.exam_attempt_id IN (61,62)` = 0 行；`sts_exam_attempt_question` 同范围 = 0 行；`sts_exam_candidate.exam_candidate_id=60` 回到 `NOT_STARTED / attempt_count=0 / best_score=NULL / final_attempt_id=NULL`；错题处置水位 `user_id=2` 的 `handled_wrong_key` 与 `wrong_disposition` 非空行数均为 **0**。

## 第五轮 UI 迭代：页头设计感 + 两个入口 + 练习与模拟记录（2026-10-08）

用户反馈原文要点：「优化练习与模拟记录的样式；再优化"学习中心"和"考试中心"的这两个入口样式，同时把全局的 `portal-list-page__heading` 的样式增加设计感；加快完成速度」，随后补充「两个入口是"管理关注课程"和"查看正式考试与所有课程随堂测试"两个」。

本轮为纯样式迭代，**不改契约、不改权限、不改数据**；几何零回归为硬约束。

### 1）全局页头 `portal-list-page__heading` 增加设计感

`frontend/pc-web/src/styles/portal.less`：

- 加 `position:relative; overflow:hidden;` + `box-shadow:0 12px 28px rgb(21 94 239 / 18%)`。
- `::before` 斜向柔光带（`rotate(18deg)` 的浅色条），`::after` 右上角 `radial-gradient` 光晕。
- `> * { position:relative; z-index:1; }` 保证内容压在装饰层之上。
- **内边距 32px、圆角 12px 保持不变**（几何零回归）。

回归证据（`baseline.mjs`）：`/portal/class` 与 `/user-center/practices` 均实测 `heroRect{x:16,w:1408}`、`heroRadius:"12px"`、`heroPad:"32px"`、`heroCount:1`。

### 2）新增全局类

| 类 | 用途 |
| --- | --- |
| `.portal-heading__actions` | 页头右侧动作区（胶囊入口 + 刷新按钮） |
| `.portal-entry-link` | 白底半透明胶囊入口：`height:32px; padding:0 14px; border-radius:999px; background:rgb(255 255 255 / 20%); color:#fff`，hover 提到 34% 并 `translateY(-1px)` |
| `.portal-list-card__tags` | 卡片标签行容器 |
| `.portal-chip` / `--plain` / `--success` / `--warning` | 通用小标签（原在页面 scoped 内，本轮上提到全局） |
| `.portal-section-head` / `portal-section-title` / `portal-section-hint` | 页内分区头（标题 + 计数提示） |

### 3）两个入口胶囊化

- 学习中心：`frontend/pc-web/src/views/portal/class/index.vue` — 页头右侧 `portal-heading__actions` 内 `RouterLink.portal-entry-link` → `/user-center/courses`，文案「管理关注课程」，图标 `Star` + `ArrowRight`，旁挂「刷新」按钮。
- 考试中心：`frontend/pc-web/src/views/portal/exams/index.vue` — 同结构，文案「查看正式考试与所有课程随堂测试」，图标 `Tickets` + `ArrowRight`。
- H5：`frontend/mobile-app/src/pages/exam/index.vue` 新增 `.hero-entry` 胶囊（`border-radius:999rpx; background:rgb(255 255 255 / 20%); font-size:24rpx; line-height:60rpx`），实测 `{"w":222,"h":31,"bg":"rgba(255, 255, 255, 0.2)","radius":"519.48px"}`。

### 4）练习与模拟记录重设计

`frontend/pc-web/src/views/user-center/practice/index.vue`：

- 原 7 行流水账 `portal-list-card__meta` 全部删除。
- 新结构：分区头（「练习与模拟记录」+「共 N 条，含全部历史作答」）→ `portal-list-card__header`（标题 + 模式 `portal-chip`，`SIMULATION` 用 `--warning`）→ `portal-list-card__tags`（状态 chip：`ANSWERING` 用 `--warning`、其他 `--success`；来源 chip 用 `--plain`）→ 题干 → `record-card__stats` 灰底统计条（`已答`/`正确`/`错误`/`正确率`/`用时`，`dt` 12px 次要色、`dd` 15px/600/`tabular-nums`，`flex + gap:var(--space-5)`）→ `record-card__time`（开始 / 提交）→ `portal-list-card__actions--split`（左「待判定 N 题」，右主按钮「继续作答」/「查看结果」）。
- 分页 `layout` 补 `total`。

采集证据（`pc-records.mjs`）：`{"sectionHead":"练习与模拟记录 共 53 条，含全部历史作答","recordCount":20,"firstCard":"端到端验证试卷 练习模式 作答中 专项训练 端到端验证试卷 已答 0 / 3 用时 2分15秒 开始 2026-10-08 16:37:24 继续作答","statsBg":"rgb(249, 250, 251)"}`。

### 静态校验（真实退出码，PC/H5 串行避免 `@shared/types` 声明竞态）

| 项 | 结果 |
| --- | --- |
| PC `vue-tsc --noEmit` | **0** |
| PC 定向 lint（本轮改动文件，`--max-warnings=0`） | **0** |
| H5 `vue-tsc --noEmit` | **0** |
| H5 定向 lint（`exam/index.vue`、`exam/notice.vue`、`personal-exam/index.vue`） | **0** |

既存告警（非本轮引入，已用 `git show HEAD:` 送 `--stdin` 复核）：`src/views/portal/exams/index.vue` 的 `<img/>` self-closing（`vue/html-self-closing`）在 HEAD 版本同样报（HEAD 行 288、本轮行 293，仅行号位移 5 行）。

### 接口回归

`verify-center.mjs` **25/25 PASS**（含 P.7 退出登录后旧令牌失效 `http=401 code=1002`）。

## 复验账号与资源坐标（2026-10-08 17:10 实测）

本节为本批次全部验收所用的账号清单，口径为「真实登录 + 身份回读」，非凭记忆列举。复验脚本
`.workbuddy/tmp/qa-batch3/accounts-check.mjs`（登录并回读 `/api/system/users/profile`）。

### 一、本批次实际使用的账号

| 记号 | 企业编码 | 工号 | 口令 | user_id | org_type | 用途 | 当前实测 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A | `DEMO` | `admin` | `admin123` | 2 | `ENTERPRISE` | 全部 PC/H5 页面与接口验收主账号 | **PASS** `http=200` |
| B | `DEMO` | `operator01` | `admin123` | 3 | `ENTERPRISE` | 跨用户隔离对照（收藏/关注/错题互不可见） | **PASS** `http=200` |
| C | `M5QA_TI_20260930` | `admin` | `admin123` | 9 | `TRAINING_INSTITUTION` | 跨租户隔离对照 | **PASS** `http=200` |

三账号登录请求：`POST /api/auth/login`，body
`{identityType:'local', authParams:{orgCode, userCode, password: sha256(明文)}}`，
鉴权头 `Authorization: Bearer <token>`。

菜单回读（确认角色差异真实存在，可用作隔离断言的锚点）：

- A（user 2，role 1 管理员）：顶层含 `门户 / 模块首页概览 / 应用中心 / 9800 企业工作台 / 200 系统管理`。
- B（user 3，role 2 学员用户）：顶层**仅 `门户`** —— 因此无管理端入口，是理想的隔离对照。
- C（user 9，role 6 租户内置管理员）：顶层含 `9800 企业工作台`（培训机构侧），`orgType=TRAINING_INSTITUTION`。

### 二、当前后端确实运行本次代码（否则复验现象对不上）

`GET /api/exam/learner/assessment-tasks`（A 会话）实测 `http=200 code=0 total=11`：

- 响应字段含 `examType` 与 `action` **均为 true**（本轮契约扩展已生效）。
- `examType` 分布 `{ONLINE:4, OFFLINE:2, null:5}`；`action` 分布 `{START:4, VIEW_RESULT:4, WAIT_REVIEW:1, null:2}`。
- 线下样本 `exam-47`（`examId=24`）、`exam-35`（`examId=14`）实测 **`available=false`、`action=null`** ——
  D1 缺陷修复在线生效，卡片不再可点。

### 三、复验用到的关键资源 ID

| 资源 | ID | 说明 |
| --- | --- | --- |
| 学员数据主体 | `user_id=2` | 错题处置水位复位对象（`sts_question_user_state`） |
| 隔离对照主体 | `user_id=3` | B 账号 |
| 线上正式考试 | `examId=34`（`assessment_execution_id=43` / `candidate=60`） | 「进行中」场景样本，须知页应显示「开始答题」 |
| 线下正式考试 | `examId=24`（`candidate=47`）、`examId=14`（`candidate=35`） | 线下样本，须知页**不得**出现「开始答题」 |
| 本轮生成的答卷 | `sts_exam_attempt.exam_attempt_id ∈ {61,62}` | **已删除**（复位后为 0 行） |

### 四、复验前置动作

1. 起后端 20080（运行本次代码）；前端需自行起 PC `5173` / H5 `5174` —— **本次核验时两个前端 dev server 已不在监听**
   （`curl --noproxy '*' 127.0.0.1:5173/5174` 连接被拒），后端 20080 在线。
2. 跑 UI 复验前先复位错题水位（`run-suite.sh` 已内置，等价 SQL）：
   `UPDATE sts.sts_question_user_state SET handled_wrong_key=NULL, wrong_disposition=NULL WHERE user_id=2 AND handled_wrong_key IS NOT NULL;`
3. 表列名易错点：`sts_exam_attempt` 主键为 **`exam_attempt_id`**（不是 `id`）；
   `sts_exam_candidate` 状态列为 **`candidate_status`**（不是 `status`）。

### 五、非本批次账号（避免混淆）

- `REGULATOR` / `regulatorAdmin`（user 8、org 1 监管平台、role 5 监管管理员）：用于 **M5 监管侧**验收，**不属于本批次**。
  其口令于 2026-09-30 授权重置，登录摘要存 `.workbuddy/tmp/regulator-hash.txt`，
  **明文未落盘**（`credential = BCrypt(SHA-256(明文))` 单向哈希，不可反推）。需要用时按
  `.workbuddy/tmp/make-regulator-credential.py` 重设，回滚快照
  `.workbuddy/tmp/rollback-regulatorAdmin-credential-20260930.sql`。
- `DEMO` 下另有 `superAdmin`(user 1)、`stu_e2e_01`(user 4)、`m4-learner-01`(5)、`m4-filler-01`(6)、
  `m4-expert-01`(7)、`M5QA_TI_20260930/m5Viewer`(user 10) —— 均为更早轮次验收账号，本批次未使用。

## 提交前检查与长文本换行修正（2026-10-08）

用户授权优化练习页面长文本并提交第三批补齐文件。截图为我的考试，两页复用 `portal.less` 卡片骨架：卡片与内容设置可收缩宽度；长标签限制最大宽度并允许中英文换行；标题移除两行截断，封面来源取消单行省略。卡片随内容增高，不以隐藏文本代替换行。

- 后端9类目标/原域回归 **112/112**（0失败、0错误）；两端请求与共享展示测试 **7/7**。
- PC/H5 typecheck通过；本批全部受影响Vue/TS定向lint通过，PC保留原门户考试img self-closing基线警告1条，H5无警告。
- PC `pnpm build`通过（既有媒体依赖外部化与大包提示保留）。
- Chrome无头浏览器使用当前真实Element Plus CSS、共用Less及两页scoped Less渲染考试/记录卡片，在1920/1440/1024宽度下注入长中文与连续英文，**6/6**卡片宽高溢出断言通过，标题完整换行。临时脚本 `.workbuddy/tmp/qa-batch3/pc-text-wrap.mjs` 不入版本库。
- 本轮登录后未加载出业务卡片，故没有把本轮样式渲染检查报为真实接口闭环或新版T6/T7业务验收；原批次真实验收沿用上文既有证据。
- 本轮未实跑MySQL IT，Docker阻塞记录保留；T6/T7新版真实登录验收仍待补证。用户确认第三批功能补充完成并授权提交，不据此伪造未执行的验收结果。
