# M2 前端 OpenAPI 回读闭环复验

- 日期：2026-08-01
- 范围：题库、试卷、考试管理、教师批阅、成绩处置、学员考试结果与课程考试项回接
- 联调边界：后端真实接口环境尚不可用，本次使用 Contract Mock；Task 6 真实联调未关闭

## 核验结果

1. `question.openapi.json`：题目发布状态由服务端筛选，当前版本素材可回读。
2. `paper.openapi.json`：`currentVersionNo` 按版本序号处理；通过版本列表取得真实 `paperVersionId`，完整回读题目、抽题规则和随机种子。
3. `exam.openapi.json`：详情回读 `policy`；批阅人可查询回显；候选人分页返回学员身份字段；反作弊事件和成绩发布流水进入教师端页面。
4. `course.openapi.json`：考试类内容项使用 `examId`；PC/移动端通过课程上下文开考；补齐既有悬空错误响应引用。
5. 四份 OpenAPI 均可按 UTF-8 解析，内部 `$ref` 无悬空引用。

## 自动化证据

- M2 定向 Node：44/44 通过。
- PC TypeScript：`vue-tsc --noEmit --ignoreDeprecations 5.0` 通过。
- PC 定向 ESLint：0 error；既有 Vue 模板格式 warning 未扩大为错误。
- PC 生产构建：通过。
- 移动端 H5 构建：通过。

## 未完成项

- 未设置 `VITE_USE_API_MOCK=false` 执行真实接口联调。
- 未执行管理员建题组卷到课程正式结果的真实数据端到端验收。
- 上述两项继续保留在 M2 Task 6。
