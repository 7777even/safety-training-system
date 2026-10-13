# 第五批纵向任务

已人工确认：2026-10-09。每次一个 Task，前三项完成汇报，再继续 T4/T5。

|任务|Owner|输入|输出及前后端影响|API/数据影响|验收与测试|
|---|---|---|---|---|---|
|[x] T1（18）|Codex|名单、正式答卷、最终成绩|后端考情/PC分析明细及XLSX，PC/H5采集|exam统计/人员/导出；无表变更|去重、分段、排名、缺考、题型、TOP5、权限、导出，Service/Controller/前端测试|
|[x] T2（25）|Codex|报名、会话、考核题目|后端课程/分类/个人统计，PC图表PNG|course统计/明细；知识点快照migration|有效时长、分类去重、知识点历史、PNG，单测/IT/前端|
|[x] T3（37）|Codex|场次、内容、录分|后端课堂统计与PC场次详情|training场次统计，无新作答|已/未评分、分布、内容，单测/IT/前端|
|[x] T4（41）|Codex|计划内容、部门、参与人|后端班级统计/人员下钻，PC饼/条图|training班级查询，无表变更|混合、去重、无部门、租户，单测/IT/前端|
|[x] T5（77）|Codex|课程事实、培训结果/冻结快照|后端计划/人员/课程记录，PC进度/完成名单|training统计/明细，无新增完成规则|实时/冻结一致、无写入、分页、租户，单测/IT/前端|

开发状态与真实验收分开登记；无真实验收不关闭原文编号。

## 2026-10-10 开发交付记录

以上勾选仅表示已确认 Task 的代码与定向验证完成，不表示功能项真实验收关闭。五项状态统一为 **开发完成，待真实验收**。

- T1：考试列表考情入口、完整汇总、分页人员、缺考筛选及 XLSX 选中/筛选全部导出；PC/H5 本人正式开考采集客户端摘要。后台替学员开启补考不采集管理员设备，缺失归入未知。
- T2：课程跨开班、分类、个人明细、有效秒数与会话、冻结知识点及 PNG。新课程执行在既有完整快照 policy 内冻结知识点；答卷按题目＋版本匹配；历史不回填。汇总排除退课，个人历史保留退课。
- T3：场次详情、已有考核和试卷关联、录分名称分组、已/未评分、成绩分布及人员。
- T4：计划部门班级整合、线上/线下 SVG 饼图和条形图、无部门单列及相同范围下钻。
- T5：计划统计、实时只读/冻结结果、人员必修进度、完成人员名单及全部可见课程记录。草稿不计完成。
- 权限：T1 `exam:exam:list`；T2 `course:offering:list`；T3 `training:plan:list AND training:offline-score:list`；T4 `training:plan:list`；T5 `training:plan:list AND training:result:list`，全部课程记录另外要求课程读取权限。复用现行租户过滤，无新权限模型。
- 数据：`V20261009140000__freeze_attempt_question_knowledge.sql`、init 快照、schema-version、Entity 已同步；无统计汇总表或新增依赖。
- 验证：后端定向 127/127；前端运行测试 8/8；PC/H5 typecheck 与定向 lint 通过；OpenSpec strict 通过。新增 4 个 MySQL IT 已编译，Docker 未启动导致执行阻塞，不记 SQL 验证通过。
- 规格与质量双轴独立静态 Review 通过，已修复发现的问题。QA/WorkBuddy 步骤：`engineering/qa/2026-10-10-batch5-business-statistics.md`；Retro：`engineering/retro/2026-10-10-batch5-business-statistics.md`。
- 未进行真实登录、浏览器渲染、真机或业务数据库验收；保留原工作区改动，未提交或推送。
