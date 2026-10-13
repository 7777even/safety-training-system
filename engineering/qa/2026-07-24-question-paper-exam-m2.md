# QA：M2 题库 / 试卷 / 考试后端（lims）

日期：2026-07-24  
负责人：lims  
Change：`openspec/changes/2026-07-24-implement-question-paper-exam`

## 范围

仅后端：`com.sts.question` / `com.sts.paper` / `com.sts.exam`；OpenAPI；migration；课程内容项 EXAM/QUESTION_BANK/PRACTICE 解禁。不含 frontend、转码、仿真/人脸。

## 验证命令与结果

零 DB（排除 IT）：

```bash
cd backend
mvn -pl sts-service-admin -am test \
  -Dtest='*Question*Test,*Paper*Test,*Exam*Test,*Knowledge*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：**Tests run: 13, Failures: 0, Errors: 0**

课程回接回归（含 EXAM 解禁）：

```bash
cd backend
mvn -pl sts-service-admin -am test \
  -Dtest=CourseItemServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：**Tests run: 7, Failures: 0, Errors: 0**

带 DB IT：

```bash
cd backend
mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest='QuestionMapperIT,PaperMapperIT,ExamMapperIT' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：**Tests run: 3, Failures: 0, Errors: 0**

## 交付物

| 项 | 路径 |
|---|---|
| migration | `database/migration/V20260724160000__create_question_paper_tables.sql`（含 import_batch） |
| migration | `database/migration/V20260724170000__create_exam_tables.sql` |
| schema IT | `backend/sql/schema-question.sql` / `schema-exam.sql` |
| OpenAPI | `docs/api/question.openapi.json` / `paper.openapi.json` / `exam.openapi.json` |
| 权限 | `backend/sts-service-admin-init/.../menus.json` question/paper/exam:* |

## 错误码

- question：6000–6199  
- paper：6500–6599  
- exam：7000–7199  

## 已知限制 / 后续

1. 题目导入为 JSON 批量写入 import_batch（templateType=EXCEL），真实 Excel/POI 与 WORD 未做。  
2. 随机卷在发布时固化抽题结果，不在开考时再抽。  
3. 防作弊仅事件落库。  
4. 课程完成态钩子提供 `ExamAttemptService.hasPublishedPass`；进度自动回写可再加深。  
5. COURSE-06～08 前端仍延期。

## 结论

M2 后端纵向闭环可关闭（lims 口径）；前端联调另开。
