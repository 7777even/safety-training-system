# Design：学员考试封面读取

## 1. 结论

`coverFileId` 仍只存在于 `Exam`，不进入学员任务 VO。读取封面以 `examId` 为授权边界：先确认当前登录人能看见该已发布独立考试，再打开考试行上的文件。接口不接受客户端传入文件
ID。

可见性与 `GET /api/exam/learner/tasks` 一致：已发布 / 进行中 / 已结束，且当前用户是该独立考试
Candidate。跨机构本人任务可见，不按当前机构裁剪。

## 2. 列表

不改 `ExamLearnerTaskVO`。前端用任务上的 `examId` 请求封面接口；无封面时按 404 / `7071` 显示占位。

## 3. 下载

```http
GET /api/exam/learner/exams/{examId}/cover?download=false
```

权限：`exam:learner:task:list`。

成功响应是原始图片字节，不包 `Result`。失败仍走统一 JSON 错误外壳。

处理顺序：

1. 考试不存在或状态不是 `PUBLISHED` / `RUNNING` / `ENDED` 返回 `7001`。
2. 没有独立执行对象，或当前用户不是该执行对象的 Candidate，同样返回 `7001`，不暴露存在性。
3. `coverFileId` 为空、文件记录不存在或对象不可读返回 `7071`。
4. 成功返回图片字节；`Content-Type`、`Content-Length`、`Content-Disposition`、`ETag` 与课程封面读取一致。`download=true` 为
   attachment，否则 inline。

ETag 格式：`"{fileInfoId}-{version}-{fileSize}"`。

## 4. 数据与契约

- 数据库：无变化。
- API：仅新增上述 GET；任务列表字段不变。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
