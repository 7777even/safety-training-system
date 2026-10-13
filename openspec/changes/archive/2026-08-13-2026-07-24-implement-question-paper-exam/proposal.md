# Proposal：实现题库 / 试卷 / 考试后端（M2）

## 背景

M1 课程闭环后端已完成。M2 需要题库、组卷、考试作答、批阅、成绩发布，并回接课程考试类内容项。评审 DDL 已归档于
`design-safety-training-mysql-schema` 的 `03_question_paper.sql` / `04_exam.sql`。

## 目标

交付 lims 纯后端 M2 纵向闭环：

1. 知识点树、题库分类/题库、题目多题型与不可变版本（QUESTION-01）。
2. 题目素材关联、JSON/CSV 批量导入（经公共 import_batch）、用户错题状态（QUESTION-02）。
3. 固定/随机/自定义试卷与不可变试卷版本快照、组卷预览（PAPER-01）。
4. 考试定义、考生、负责人/批阅人、发布（EXAM-01）。
5. 开考快照、暂存、幂等交卷、超时交卷（EXAM-02）。
6. 客观题自动判分、主观题待阅、批阅只追加、成绩发布（EXAM-03）。
7. 补考新尝试、练习重置、课程内容项 EXAM/QUESTION_BANK/PRACTICE 解禁与成绩回接钩子（EXAM-04）。
8. OpenAPI、权限种子、进度表与 QA（EXAM-05）。

## 用户价值

管理员可建题组卷并发布考试；学员可作答并查看已发布成绩；教师可批阅主观题；课程可挂接考试类内容项并按正式成绩判定完成。

## 功能范围

- 包：`com.sts.question` / `com.sts.paper` / `com.sts.exam`
- migration + `backend/sql/schema-question.sql` / `schema-exam.sql`
- `docs/api/question.openapi.json` / `paper.openapi.json` / `exam.openapi.json`
- 零 DB 核心单测 + Mapper IT
- menus.json 权限码；错误码 question 6000+、paper 6500+、exam 7000+（避开课程 50xx）

## 非目标范围

- 任何 `frontend/` 改动、页面、Mock、UI
- 真实媒体转码 / 预览派生
- 仿真软件、仿真练习/考试、人脸识别
- 完整防作弊客户端策略（仅事件落库 API）
- WORD 模板导入（本期 JSON/CSV + import_batch，EXCEL 路径后续兼容）

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
