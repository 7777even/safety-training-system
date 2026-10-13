# Retro：M3 培训执行闭环 Task 3

## 做得好的地方

- 把逐内容投影和结束快照一次建齐，查询与冻结共用同一粒度，避免以后再拆 JSON。
- 结束门禁返回稳定 reasonCode 与 participantId/contentId，而不是只给一句“不能结束”。
- 旧考试入口继续可用，统一 enter 作为唯一派生身份的入口。

## 问题

- 教师处理线下事实时，管理权限与场次教师权限叠在同一 upsert/confirm 接口，需要用登录态加权限码分流，而不是新建权限模型。
- 本机 Docker 不可用，投影唯一键和 Flyway 升级路径只能等环境补跑 IT。

## 改进

- 计划生命周期（start/end/cancel）与结果投影分开服务，避免 TrainingPlanServiceImpl 继续膨胀。
- 未发布成绩、签退学时和考试不计培训学时必须有独立零 DB 用例，避免被平均分逻辑掩盖。
