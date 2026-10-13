# Design：批阅意见读取与教师批阅界面优化

## 读取模型

管理端 `GET /api/exam/attempts/{examAttemptId}` 保持 `exam:attempt:list` 权限不变。每个 `questions[].answer` 增加可空
`reviewComment`，其值为该 `examAnswerId` 最近一次有效批阅记录的 `review_comment`；无记录或最近记录未填写意见时返回 `null`。

`GET /api/exam/learner/attempts/{examAttemptId}` 继续使用同一 `ExamAnswerVO`，但服务端必须显式裁剪 `reviewComment` 为
`null`，不以客户端隐藏作为安全边界。

## 查询与性能

管理端作答详情在已批量读取答案后，按答案 ID 一次性读取对应的最新批阅记录，构造 `examAnswerId -> reviewComment`
映射；不得对每道题单独查询。

“最近一次”按批阅记录的 `reviewedTime` 降序，同一时间时以主键降序确定，和教师任务列表的最近批阅摘要保持一致。

## 前端呈现

共享 `LearnerExamAnswer` 增加 `reviewComment?: string | null`。教师批阅页加载作答详情后，用该字段初始化意见输入框；保存后重新加载仍以服务端返回值为准。

主观题卡片采用紧凑的两层信息区：上层为作答内容、参考答案和状态摘要，下层为“本次得分”“批阅意见”和保存按钮。空意见保持可编辑，不显示伪造值。

## API 与权限

- 修改 `ExamAnswerVO.reviewComment`：中文说明、`string`、`nullable: true`、example。
- 管理端作答详情说明该字段仅供 `exam:attempt:list` 授权的内部处理使用。
- 学员作答详情说明该字段固定裁剪，不返回内部批阅意见。

## 风险与取舍

批阅意见属于内部评分事实，不能随通用 VO 自动暴露给学员。采用服务端在学员读取路径显式置空，避免以后前端复用组件时泄露。复用既有批阅记录表，不增加
schema 或 migration。
