# Proposal：学员按考试 ID 受控读取封面

## Why

学员考试任务需要展示封面，但没有按考试 ID 读取封面字节的接口。列表已有 `examId`。`coverFileId`
是内部文件指针，不能当下载地址，也不能交给通用文件接口拉取私有对象。

## What Changes

- 新增 `GET /api/exam/learner/exams/{examId}/cover`。
- 不改学员任务列表字段。

## Capabilities

### New Capabilities

- `learner-exam-cover`：学员按考试 ID 受控读取封面。

### Modified Capabilities

- 无。

## Impact

- `ExamLearnerController`、`ExamLearnerService`、`docs/api/exam.openapi.json`。
- 不改数据库，不改考试封面上传，不改 `GET /api/exam/learner/tasks` 的字段。

## 背景

考试实体已有 `coverFileId`，管理端也可上传。学员端真正缺的是按 `examId` 读出封面字节。任务列表透出 `coverFileId`
不能完成展示；前端以后若需要「无封面不请求」再单开。

## 功能项追溯

- **功能项 16**：安排考试时可设置考试信息；调研文档要求考试封面。本 Change 只补学员受控读取，不改管理端上传。

## 目标

1. 学员只能按考试 ID 读取自己可见考试的真实封面。
2. 考试不可见时返回 404 / `7001`，不暴露存在性。
3. 可见但无封面或文件不可读时返回 404 / `7071`。
4. 默认内联查看，`download=true` 时附件下载。

## 非目标范围

- 不改前端、Mock、联调。
- 不在学员任务列表增加 `coverFileId` 或其他封面标志。
- 不新增封面上传接口，不改 `exam-covers` 目录或 migration。
- 不把任意 `fileInfoId` 暴露为下载入口。
