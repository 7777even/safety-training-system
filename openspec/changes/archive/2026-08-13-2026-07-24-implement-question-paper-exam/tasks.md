# Tasks：题库 / 试卷 / 考试后端（M2）

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：QUESTION-01 知识点 / 题库 / 题目不可变版本

- 状态：已完成
- Owner：lims
- 输入：评审 DDL 03；本 Change design
- 输出：migration（含 import_batch 若缺失）、schema-question.sql、question 包 CRUD/发布、零 DB + IT
- API 依赖：`docs/api/question.openapi.json`（本 Change 同步）
- 数据影响：`sts_question_*` + `sts_common_import_*`
- 验收标准：知识点树；题库分类/题库；JUDGE/SINGLE/MULTIPLE/FILL/ESSAY；发布/更新写新版本且旧 version_no 只读
- 测试方式：零 DB Service；Mapper IT
- [x] migration `V20260724160000__create_question_paper_tables.sql`（含 import_batch）
- [x] Entity/Mapper/Service/Controller 知识点、分类、题库、题目版本
- [x] 零 DB + IT

## Task 2：QUESTION-02 素材关联 / 导入 / 错题状态

- 状态：已完成
- Owner：lims
- 输入：Task 1
- 输出：asset 关联 API；JSON 导入 + import_batch；user_state upsert
- 验收标准：仅 fileId/assetId 关联无转码；导入写批次与题目或校验错误；错题 upsert
- 测试方式：零 DB 导入校验路径
- [x] 题目素材关联
- [x] JSON bulk import + import_batch
- [x] 用户错题状态 upsert

## Task 3：PAPER-01 组卷与不可变试卷版本

- 状态：已完成
- Owner：lims
- 输入：Task 1
- 输出：paper 包；FIXED/RANDOM/CUSTOM；发布快照；预览
- 验收标准：随机规则存储并在发布时固化题目快照；预览可复盘
- 测试方式：零 DB 组卷/发布；Mapper IT
- [x] paper Entity/Service/Controller
- [x] compose / preview / publish

## Task 4：EXAM-01～04 考试定义 / 作答 / 判分 / 补考与课程回接

- 状态：已完成
- Owner：lims
- 输入：Task 3；BASE-03 audience
- 输出：migration exam；exam 包；课程内容项类型解禁
- 数据影响：`sts_exam_*`
- 验收标准：开考冻结快照；交卷幂等；客观 AUTO / 主观 PENDING_REVIEW；批阅只追加；成绩发布；补考/练习重置；EXAM/QUESTION_BANK/PRACTICE
  可建
- 测试方式：零 DB 判分/发布；Mapper IT
- [x] migration `V20260724170000__create_exam_tables.sql`
- [x] 考试 CRUD / 考生 / 批阅人 / 发布
- [x] attempt start/save/submit/timeout
- [x] auto-grade / review / publish-score
- [x] retake / practice-reset / course item hook

## Task 5：EXAM-05 OpenAPI / 权限 / 进度 / QA

- 状态：已完成
- Owner：lims
- 输出：三份 OpenAPI；menus；进度表；QA
- 验收标准：契约字段说明与 example；QUESTION/PAPER/EXAM/M2 已完成；COURSE-06～08 仍延期
- [x] `question.openapi.json` / `paper.openapi.json` / `exam.openapi.json`
- [x] menus.json 权限；docs/AGENTS.md + openapi guide 表
- [x] 进度表 + QA `engineering/qa/2026-07-24-question-paper-exam-m2.md`
- [x] 目标测试命令通过
