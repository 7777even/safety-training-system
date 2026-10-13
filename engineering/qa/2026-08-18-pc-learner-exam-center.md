# QA：PC 学员考试中心

日期：2026-08-18  
OpenSpec Change：`2026-08-18-implement-pc-learner-exam-center` Task 1～2

## 验收结果

- 门户边界：`/portal/exams` 以 `/portal/class` 为视觉基准，使用蓝色学习横幅和网格任务卡；页面不再显示后台式筛选表单或工作区布局。
- 契约：本人任务分页保留 `pageNum`、`pageSize` 和三个 `query.*` 参数映射；课程来源只导航至“我的课程”，不会调用独立开考接口。
- 可见性：任务、作答和结果只展示服务端返回且允许学员查看的成绩、答案和解析字段。
- 定向测试：`node --test frontend/pc-web/tests/learner-exam-center.test.mjs`，6/6 通过；其中门户视觉边界用例先失败，调整页面后通过。
- 工程验证：PC Web `typecheck`、考试相关源码 ESLint、PC Web `build` 与 `git diff --check` 通过。
- OpenSpec：`openspec validate 2026-08-18-implement-pc-learner-exam-center --strict` 通过。
- 开考跳转补正：`POST /api/exam/attempts/start` 成功后通过命名的 `PortalExamAttempt` 内部路由进入作答详情；若守卫拦截、改道或跳转失败，页面明确提示并保留幂等令牌以便刷新后续答。定向测试扩展为 8/8 通过。
- 交卷答题回看：手动或超时交卷进入结果页后，成绩未发布时可查看题目与本人提交的选择项/文本答案；评分、对错、标准答案和解析仍受服务端可见性字段约束。定向测试扩展为 10/10 通过。
- 考试中心入口：独立考试处于 `WAIT_REVIEW` 时，任务卡直接显示“查看答题情况”并以 `currentAttemptId` 进入答题回看；定向测试扩展为 11/11 通过。

## 未通过或未执行项

- PC Web 全量 `lint` 未通过，原因是本次未修改的 `src/components/pdf-viewer/PdfViewer.vue` 存在 5 个 `no-undef`（`WheelEvent`、`EventTarget`、`KeyboardEvent`、`MouseEvent`、`EventListenerOptions`）错误；考试相关文件的定向 ESLint 已通过。
- 真实非生产环境联调尚未执行，仍需要管理员、学员、批阅教师和成绩发布账号完成发布、作答、批阅、发布成绩、查看结果的闭环；Mock 不作为该结论的替代。

## 双轴 Review

- 规格符合性：通过。任务中心的入口分流、分页字段、结果可见性和门户视觉边界均符合本 Change；未改变 OpenAPI、权限或数据库。
- 代码质量：通过。变更限制在学员考试页面、契约类型/API/Mock、定向测试及规范记录；无新增依赖，页面和业务函数保留中文注释与 scoped Less。
