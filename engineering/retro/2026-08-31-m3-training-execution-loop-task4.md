# Retro：M3 培训执行闭环 Task 4

## 做得好的地方

- 把抽题变成可单测的纯函数，种子复现、规则定位和跨规则去重不必绑在 Attempt 服务上。
- 计划专属考试改为只消费发布快照，来源考试当前策略不再泄漏进已发布计划。
- 教师接口把准入权限和数据归属拆开，避免 `hasPermission(同一码)` 恒为管理员代办。

## 问题

- `information_schema.statistics` 对复合唯一索引按列计数，`COUNT(*)=1` 在真库会失败；应用 `COUNT(DISTINCT index_name)`。
- `WEIGHTED` 别名曾让加权退化为算术平均，必须与 OpenAPI 枚举逐字对齐。
- 本机 Docker 曾不可用；补跑后 Admin MySQL IT 205 与 Admin-init IT 1 均通过，复合索引断言已在真库验证。

## 改进

- 发布冻结的考试策略字段与执行生成命令字段保持同一份清单，避免再从 live Exam 抄运行参数。
- 成绩投影以考试域正式结论为准，培训域不再用本地及格线重判 PASSED/FAILED。
