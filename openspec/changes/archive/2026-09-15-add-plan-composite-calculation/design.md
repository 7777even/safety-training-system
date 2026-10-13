# Design：培训计划内综合成绩计算与确认

## 交互与数据边界

结果抽屉已经以计划 ID 和参训人 ID 查询培训结果，因此综合成绩操作复用该上下文。综合表单版本只从当前计划 `evaluationForms` 中选择 `usageType=COMPOSITE` 且 `status=1` 的绑定；页面不提供选择器或 ID 输入。

计算批次按当前计划和参训人分页查询。计算成功仅新增 `CALCULATED` 批次；确认最新批次后才成为正式结果。计划不是 `RUNNING`、未绑定启用综合表单、或调用方缺少相应按钮权限时，只展示中文原因，不展示无效操作。

## 接口

复用既有接口和请求模型：

- `GET /api/evaluation/calculation-runs`：按 `trainingPlanId`、`trainingParticipantId` 查询历史。
- `POST /api/evaluation/calculation-runs/calculate`：提交当前计划、参训人和绑定表单版本。
- `POST /api/evaluation/calculation-runs/{calculationRunId}/confirm`：确认最新 `CALCULATED` 批次。

计算、确认后同时刷新批次历史和培训结果，使 `compositeScore`、`officialCalculationRunId` 与页面一致。失败直接保留后端中文消息，不在前端伪造结果。

## 权限

按钮显示分别依据 `evaluation:calculation:calculate`、`evaluation:calculation:confirm`、`evaluation:calculation:list`；后端仍负责真实鉴权。

## 验证

先新增 API 请求映射与结果抽屉上下文边界的定向 Node 测试，确认红灯后实现。完成后执行目标测试、PC typecheck、定向 ESLint 与差异检查；真实环境按管理员权限完成计算、确认和结束计划验收。
