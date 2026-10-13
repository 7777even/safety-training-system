# Retro：M3 培训执行闭环 Task 5

## 做得好的地方

- 把计划专属考试从「冻结 paperVersion + 回读来源 Exam」改成自足的 schemaVersion=2 快照，来源考试改名、改策略、改批阅人或删除后仍能建执行。
- Reviewer 绑定到新的 AssessmentExecution，主观题批阅不再依赖来源考试的人员关系。
- `sourceStatus` 进入投影和 v2 最终快照，考试 PASSED 与培训 COMPLETED 可区分，统计下钻不必再跨域猜状态。
- 培训题库与课程题库用已冻结的 `sourceType` 分流，避免课程全量练习泄漏到培训执行。
- 复核缺口用失败测试钉住：补考窗口回填、开考 hash、归档 v1 读取，避免「测试绿但规格未覆盖」。

## 问题

- Task 4 只冻结部分考试字段后，provision 仍会回读来源 Exam/Policy/Reviewer；二次复核才发现批阅人无法工作、sourceStatus 被
  resultStatus 冒充。
- 既有已发布计划若仍是 v1 快照，Task 5 会在生成执行时返回 8142；不能静默补齐，必须取消后重建。
- 完整 Admin IT 日志过长时终端捕获会截断，需用 surefire 报告汇总确认 206/206。
- 首轮 Task 5 把「v1 快照可读取」写进 QA，但查询仍读投影表、没有 v1 解析用例；补考时间和开考 hash 也缺测试。复核结论「需修改」是对的。

## 改进

- 发布冻结字段清单与执行生成命令字段保持同一份 schemaVersion 契约，禁止「先冻结一部分、运行期再回读来源补齐」。
- 成绩投影同时保存来源原始状态和培训统一状态；归档后查询必须按快照 schemaVersion 解析，不能回退到可变投影。
- 开考路径在创建 Candidate/Attempt 前校验执行快照 hash；hash 算法与冻结写入共用同一实现。
- 新增列只加可空字段和新 V 文件，不改既有 migration，也不猜测已归档 v1 历史状态。
