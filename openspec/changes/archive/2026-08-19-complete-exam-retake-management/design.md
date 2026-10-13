# 设计：考试补考管理与成绩状态展示

## 约束与依据

- 唯一接口契约：`docs/api/exam.openapi.json`。
- 考试创建和更新继续通过 `ExamCreatePayload.policy`、`ExamUpdatePayload.policy` 写入补考策略。
- 开补考仅调用 `POST /api/exam/candidates/{examCandidateId}/retake?clientAttemptToken={token}`，权限语义沿用契约的 `exam:attempt:start`。
- 不修改任何后端接口、OpenAPI、权限或数据结构。

## 补考策略

在“允许补考”开启后展示并保存以下已有 `policy` 字段：

| 字段 | 页面文案与行为 |
| --- | --- |
| `retakeScope` | 补考范围；支持“不及格”“不及格或未参加”“仅未参加”。空值按“不及格”展示和提交。 |
| `retakeStartTime` / `retakeEndTime` | 补考开放时间；均可为空，表示不限制相应一端。 |
| `retakeMaxCount` | 最大补考次数；不含首次考试。 |
| `retakeScoreMode` | 成绩取值方式；最高分或最近一次。 |
| `isRetakeRecordVisible` | 是否向学员展示补考记录。 |

原考试的开放时间与作答时长不在补考操作中重复设置；迟到禁考规则不适用于补考。

## 开补考交互

1. 从考试情况和全局成绩列表进入成绩处置时，路由携带 `examCandidateId`，不改变路由定义。
2. 成绩处置页读取考试详情的 `policy`。只有补考已启用且入口具备考生 ID 时展示“开补考”按钮；权限显示采用 `exam:attempt:start`。
3. 点击后给出“将为该学员开通一次补考资格”的确认。确认后创建本次操作的 `clientAttemptToken`，失败重试保留该令牌；成功后清除令牌、提示已开通并刷新页面数据。
4. 请求只传考生 ID 和令牌。响应成功时不在前端修改正式分、考试时间、试卷、范围或次数。
5. 当前契约未在单次作答详情中同时提供补考次数、补考窗口、完整考生状态和正式发布时间，前端不伪造预判。后端返回 7051、7052、7054、7055、7056、7011 时，直接显示返回的业务提示。

## 枚举展示

补齐并集中复用以下中文映射：

- 作答状态：包括 `PUBLISHED` 等真实返回值；
- 成绩发布方式：`SINGLE`、`MANUAL`、`AUTO`；
- 成绩结果：`PASSED`、`FAILED`；
- 补考范围：`FAILED`、`FAILED_OR_ABSENT`、`ABSENT`。

未知值不直接显示英文枚举，使用“其他状态”或“其他方式”等中文兜底文案。

## 风险与取舍

补考资格的最终判断属于服务端规则。基于不扩展当前 OpenAPI 的约束，页面不尝试计算补考次数或窗口，以避免前后端规则漂移；用户点击后以明确的业务错误提示反馈实际结果。
