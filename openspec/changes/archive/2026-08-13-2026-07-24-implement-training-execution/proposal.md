# Proposal：实现培训标准 / 计划 / 执行后端（M3）

## 背景

M0～M2 后端已完成（含监管关系 BASE-04、课程、题库试卷考试）。M3 需打通培训标准与计划输入，以及培训计划线上线下执行，形成参训分项结果事实。评审
DDL：`05_training.sql`；评价表单定义取自 `06_*` 中 `sts_evaluation_form*`（TRAIN-03，不含问卷投放与综合计算批次）。

## 目标

交付 lims 纯后端 M3 纵向闭环：

1. TRAIN-01：培训类型、培训标准与规则（监管关系复用 BASE-04，不重复建）。
2. TRAIN-02：培训模板分类 / 模板 / 阶段 / 内容；发布后可引用；跨机构引用走已有资源授权。
3. TRAIN-03：评价表单定义与不可变版本（含项目；评分规则最小集）。
4. TRAIN-04：培训计划、阶段、内容、评价表单绑定、参训人员（audience 圈人）、发布时冻结来源快照与哈希。
5. TRAIN-05：发布后幂等生成课程开课 / 考试实例关联 / 线下场次执行实例。
6. TRAIN-06：签到、线下成绩、附件、奖惩；教师任务按线下场次讲师视角查询。
7. TRAIN-07：OpenAPI、权限、进度表、QA 关闭 M3。

## 用户价值

园区/机构可维护类型与标准；机构可引用模板建计划、圈人发布；学员参训；教师处理签到与线下评分；计划结束后具备可追溯的分项执行事实（综合结果计算属
M4）。

## 功能范围

- 包：`com.sts.training`（计划执行）、`com.sts.evaluation`（表单定义/版本，TRAIN-03）
- migration + schema IT 快照
- OpenAPI：`training.openapi.json`、`evaluation-form.openapi.json`
- 零 DB 核心单测 + Mapper IT
- menus 权限；错误码 training 8000+、evaluation 8500+

## 非目标范围

- 任何 `frontend/` 改动、页面、Mock、UI
- 问卷投放 / 综合成绩计算批次 / 档案证书（M4）
- 培训直投直播、仿真、人脸识别
- 真实 Excel 线下成绩 POI（可用 JSON + import_batch）
- 运营统计与监管预警（M5）

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
