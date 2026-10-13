# Design：移动端正式考试考前须知与单向开考

## 业务流程

```text
我的考试（NOT_STARTED / START）
  → 考试须知页（仅阅览，不创建 Attempt）
  → 点击“开始答题”
  → POST /api/exam/attempts/start 创建 Attempt
  → redirectTo 考试答题页（不能返回须知）
```

- `CONTINUE` 仍直达既有答题页；`VIEW_RESULT` 和 `WAIT_REVIEW` 不进入须知页。
- 须知页“返回”只回到考试任务列表，未创建作答。
- 开考接口的既有服务端校验与幂等令牌仍是最终开考依据；前端单向路由不替代服务端状态校验。

## API 与数据

- 复用 PC 须知页已调用的 `GET /api/exam/exams/{examId}`；移动端新增只读 API 函数和与 PC 一致的 Mock 详情数据。
- `passScore`、`passRatio` 用于显示总分和及格分；总分由既有 PC 规则反推，任一缺失显示“未设置”。
- `isNoticeEnabled`、`noticeContent` 用于显示自定义须知；未启用或内容为空时显示通用规则。
- 不修改后端实现、OpenAPI、共享类型或考试任务列表响应。

## 移动端设计

- 新增 `pages/exam/notice.vue`，以 `examId` 读取既有考试详情；页面展示名称、总分、及格分、时长、开放时间、自定义须知和通用规则。
- 考试列表仅将 `START` 任务跳转到须知页；须知页调用现有 `startExamAttempt` 后以 `uni.redirectTo` 进入 `pages/exam/attempt`。
- 答题页不接收或导航回须知页；系统返回仅回到考试列表或由现有答题页的返回保护逻辑处理。

## 风险与取舍

- 既有详情接口要求 `exam:exam:list`；本 Change 沿用 PC 页面已经使用的权限语义。已由开发人员确认学员角色具有该既有权限；若后续真实联调仍返回 403，必须另开权限/API Change，不得在移动端绕过权限。
- 开考前须知不作为可审计确认事实；本 Change 只实现阅览与显式开始，不增加确认记录。
