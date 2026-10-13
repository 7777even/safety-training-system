# Design：课程考试资源与正式成绩回接

## 资源绑定

- `ItemCreateRequest` / `ItemUpdateRequest` / `CourseItemVO` 统一表达 `examId`。
- EXAM_LIKE 内容项必须有 `examId`，ASSET 必须有 `assetId`，两类引用互斥。
- `CourseItemService` 使用 `ExamResourceAccessHelper`：考试必须存在、`publishStatus=PUBLISHED`、`status=1`；跨机构时调用
  `ResourceGrantService.requireAccess(ownerOrg, "EXAM", examId, courseOrg, "USE")`。
- `examName`、`examPublishStatus` 只读批量填充，不能替代服务端校验。
- 类型映射固定为 EXAM→ONLINE、PRACTICE→SPECIAL_PRACTICE、QUESTION_BANK→BANK_PRACTICE；OFFLINE 不可被课程考试内容项引用。
- 更新 `examId` 只允许未产生参与事实的内容项；参与事实包括 item progress、课程上下文 Candidate 或被冻结课程版本已被开课使用。
  `isLockedAfterParticipation=1` 时任一参与事实均拒绝变更。

## 课程执行入口

新增课程域入口（路径暂定）：

```text
POST /api/course/learning/enrollments/{courseEnrollmentId}/items/{courseItemId}/exam-attempts
```

服务端从当前登录用户、报名、开课绑定版本快照和内容项取得 examId；不得接受 userId、examId、contextType 或 contextId。它幂等创建/定位：

- `contextType=COURSE`
- `contextId=courseEnrollmentId`
- Candidate 的 courseOfferingId、courseItemId、courseEnrollmentId
- 课程来源 Candidate 与 Attempt

Candidate 唯一性必须包含考试、用户、课程报名和内容项，避免同一考试被不同课程或不同内容项复用时串链。新增 migration 将非课程
Candidate 的课程字段归一为 0，并以
`(exam_id, user_id, context_type, context_id, course_enrollment_id, course_item_id, deleted)`
作为唯一键：独立/培训上下文保持原上下文唯一性，COURSE 上下文额外按报名和内容项隔离。

## 成绩发布回接

`publishScore` 在正式发布成功后，以同事务应用内调用向课程侧传递 `CourseExamResultPublished` 正式成绩事实；该事实包含
Attempt、Candidate、考试、用户、课程报名、开课、内容项、发布时间、分数和是否通过。课程侧按 Candidate 中冻结的
`courseEnrollmentId + courseItemId` 幂等更新 `course_item_progress`；不新增对前端或外部开放的 HTTP 回接接口：

- 更新 attemptCount、bestScore、lastStudyTime。
- 只有正式 PUBLISHED 且 PASSED 的结果将 EXAM/QUESTION_BANK/PRACTICE 项置为 COMPLETED 并写 completedTime。
- 重复发布或重试不得重复完成、倒退进度或覆盖更优成绩。
- 回写后重算该 enrollment 的完成态；课程完成只能读取同一课程报名上下文的正式结果。

EXAM、PRACTICE、QUESTION_BANK 均只有正式发布且 PASSED 才完成课程项。

## API 与测试

- `course.openapi.json` 更新 items create/update/list、CourseItemVO、课程考试进入接口与错误码。
- `exam.openapi.json` 仅在课程入口委托考试创建时补充互相引用说明。
- 零 DB：资源可用性/授权、锁定、上下文隔离、发布幂等回写、Controller 契约。
- MySQL IT：课程项引用、Candidate 上下文字段、进度幂等回写和跨课程隔离。
