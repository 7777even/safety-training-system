# 设计

## 已确认决策
本人接口从登录及租户上下文取得身份，不接受userId。原有管理接口不降权限。
练习会话、题目答案单独持久化，冻结题源，PRACTICE逐题反馈、SIMULATION提交后反馈，ESSAY仅记录。
错题直接读取两来源历史答卷，复用现有结果可见裁剪；按questionId合并最近可见错误版本。个人状态保存处理过的错误标识，后续新错答恢复；不修改原答卷。
54/58以机构+用户+课程共用收藏。教师目录以同机构有效TEACHER用户去重，公开姓名，不公开联系方式。

## 数据资产
新增个人练习会话和题目记录；扩展个人题目状态的错题处理标识；课程收藏、教师关注独立关系表。完整init、增量Flyway、schema-version、Entity一起更新。所有新增表含organization_id，使用现有租户拦截，不新增豁免。

## 公开接口与页面
学员练习接口位于/question/learner，收藏课程及教师关注位于/course/learning。全部本人读写；资源访问失效不可绕过，取消仍允许。
我的考试聚合正式考试、课程QUIZ，原作答链路保留，提供本人历史答卷。
PC门户学习样式、用户中心入口；移动个人中心入口及独立页面。共享类型来自OpenAPI。不引入依赖。

## 验证边界
已确认测试接口：Service公开本人行为、Controller参数/身份边界、MySQL真实持久化、两端实际交互。按纵向Task逐项Red→Green。真实登录QA缺证不关闭原功能项。

## 最终契约与资源规则

接口统一以 `/api` 为上下文，登录校验及organization_id租户拦截沿用现有链路。全部新增入口不要求管理员编辑权限；本人的用户ID不作为请求字段。

| Controller | Method / URL（省略/api） | 业务能力 |
|---|---|---|
| LearnerPracticeController | GET /question/learner/practices、sessions、wrong-questions | 分配练习、个人记录、两来源错题分页 |
| LearnerPracticeController | POST /question/learner/sessions；GET/DELETE /question/learner/sessions/{id} | 按题源+模式+令牌启动、恢复、清除 |
| LearnerPracticeController | PUT /question/learner/sessions/{id}/items/{itemId}/answer；POST .../{id}/submit | 保存单题及幂等提交 |
| LearnerPracticeController | PUT /question/learner/questions/{questionId}/favorite、wrong-questions/{questionId}/disposition | 收藏与DELETED/MASTERED个人处理 |
| LearnerAssessmentCenterController | GET /exam/learner/assessment-tasks、attempt-history | 新增聚合读模型与历史引用，原任务接口不破坏兼容 |
| LearnerCourseFavoriteController | GET /course/learning/favorite-course-catalog、favorite-courses、favorite-courses/{id}；PUT .../favorite-courses/{id} | 可访问目录、本人收藏、只读详情、收藏状态 |
| LearnerTeacherFollowController | GET /course/learning/teachers、teachers/{id}、teacher-follows；PUT .../teacher-follows/{id} | 教师目录、安全详情、本人关注 |

- PracticeStart仅接受candidateId或questionId二选一、mode、clientToken；恢复返回题源引用供模式切换。PracticeAnswer使用selected、blanks、可空text；空答案保留为未做。客户端令牌每次新会话生成一次，HTTP层重复请求复用同一令牌。
- 会话保存冻结题干、选项、答案、解析和版本，单题提交不进入正式答卷服务。启动事务采用READ_COMMITTED配合唯一键：重复并发插入等待后读取已提交的同一会话；作答/提交/清除锁定会话，错题处理及题目收藏先原子初始化再锁定状态关系。
- 错题从ExamAttempt/Answer真实记录读取，不依赖wrongCount。正式来源用getMyAttempt裁剪，COURSE_QUIZ额外走CourseAssessmentAttemptService.get校验报名/课程项；来源撤销后跳过，不扩大可见范围。按错误答案记录ID选择最近可见版本；处理标识为attemptId:answerId，答案记录ID为水位。较旧来源恢复可见时不重新收录，只有更新的错误记录恢复待练。
- 聚合分页只增加读模型，不改正式/课程状态机。正式仍走原学员任务可见范围；课程包含有效本人报名所有QUIZ，即便尚未创建Candidate。已结束课程有发布结果时允许回看。
- 收藏目录复用本人报名详情和选课大厅资格裁剪；未报名只读课程简介/教学目标。CourseFavorite/TeacherFollow用状态标志取消，ON DUPLICATE KEY更新保证重复/重新添加不撞软删除唯一键。教师实体没有现成公开头像，展示姓名首字，不新增头像字段。
- 错误码7091（练习状态/题源冲突）、7092（本人资源拒绝）、5093（课程收藏资源拒绝）、5094（教师关注资源拒绝），避免占用原5091/5092封面错误语义。
- 增量迁移20261008120000、20261008130000、20261008140000，与完整schema-question/schema-course和init/IT基线20261008140000同步。历史Flyway用例明确限制原20260930180000目标，本批新增独立旧状态表升级及init列定义对比用例。真实SQL未跑通前不声明migration已验收。

本批各编号仍单列验收；五个Task开发侧完成不等于七项业务关闭。历史裁剪复用造成逐份读取及内存分页，已以ponytail注释标出大量记录时的批量查询升级点，不额外建通用框架。
# 验收修正设计（用户2026-10-08明确授权）

- 关注课程仅管理课程；我的收藏为课程/题目两个分类，课程关系继续复用54，不增加订阅。题目分类使用 GET /api/question/learner/favorite-questions，复用本人 QuestionUserState。安全摘要只返回题干、题型、来源和本人会话入口，不返回标准答案、解析或判定；取消已有收藏不要求原资源仍有效。
- 收藏题目优先使用本人未清除会话的冻结快照，已清除会话仅保留摘要并显示入口不可用；无会话时使用原领域仍可见错题，失效题目允许取消。列表按questionId去重、分页、题干搜索，取消后返回列表不再显示。
- GET sessions 与会话详情增加 totalCount、answeredCount、questionStem、sourceType、sourceName。列表统计来自冻结题目，不返回 questions 内容；模拟未提交时正确/错误/未判定统计仍裁剪为0，正确率为空。用时沿用会话开始至提交/当前时间的累计时长，不冒充有效学习时长。
- PC/H5同步收藏分类与记录摘要。测试沿用已确认 Service公开接口、Controller安全出参、两端请求适配器；真实SQL定向IT验证本人/租户过滤及旧状态查询。无数据库结构变更。
