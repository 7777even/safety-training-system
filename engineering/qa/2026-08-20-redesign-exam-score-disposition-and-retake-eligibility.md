# QA：考试成绩处置与补考资格展示

日期：2026-08-20  
OpenSpec：`2026-08-20-redesign-exam-score-disposition-and-retake-eligibility` Task 1～3

## 验收结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 服务端补考资格摘要 | 通过 | 作答详情返回启用、范围、窗口、次数、考试状态、冻结试卷及题目版本快照可用性共同计算的资格、原因和已用次数。创建补考前复用同一判定。 |
| 冻结试卷保护 | 通过 | `ExamAttemptServiceImplTest` 覆盖试卷版本不存在、题目版本缺失时详情返回不可开补考原因，且开补考不写入作答。 |
| 发布前成绩核对 | 通过 | 成绩处置页展示考生编号、总分、服务端派生预计及格结果、逐题答案、自动分、人工分、最终分和批阅状态；待阅主观题阻断发布。 |
| 补考操作可见性 | 通过 | 仅服务端 `retakeEligibility.eligible=true` 且当前用户拥有 `exam:attempt:start` 时显示“开补考”；其余情况显示中文原因。 |
| 后端目标测试 | 通过 | `mvn -pl sts-service-admin -am -Dtest=ExamAttemptServiceImplTest '-Dsurefire.failIfNoSpecifiedTests=false' test`，26/26。 |
| 前端定向回归 | 通过 | `node --test tests/exam-score-disposition.test.mjs tests/exam-publish-lock-and-workbench-tabs.test.mjs`，6/6。 |
| 前端类型与受影响 lint | 通过 | `pnpm --dir frontend/pc-web run typecheck`；受影响源码 ESLint 通过。 |
| API 契约与差异检查 | 通过 | `exam.openapi.json` JSON 解析通过；`git diff --check` 无空白错误（仅 CRLF 提示）。 |
| 双轴 Review | 通过 | 规格符合性与代码质量 Review 均通过；复审补齐冻结题目版本、发布前预计及格结果、题型中文与长答案换行。 |

## 未执行/阻塞项

- 未在非生产环境以教师账号完成真实接口和页面交互验收；当前默认 Mock 与目标自动化测试均已覆盖交互判定。
- 全量 PC lint 仍由既有 `frontend/pc-web/src/components/pdf-viewer/PdfViewer.vue` 的 5 条 `no-undef` DOM 类型报错阻断；本 Change 的受影响文件 lint 已通过。
- 未运行带 MySQL 集成测试：本次仅增加基于既有表的只读资格计算，无 Entity、Mapper、SQL、schema 或事务落库变更。
