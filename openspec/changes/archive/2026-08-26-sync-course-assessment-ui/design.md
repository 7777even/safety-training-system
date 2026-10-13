# Design：课程考核前端契约同步

## 业务边界

- 课程内容只使用 `QUESTION_BANK -> questionBankId` 与 `QUIZ -> paperVersionId`；题库与试卷是资源，不是独立考试任务。
- 课程学员作答和课程测验阅卷使用 `/api/course/**`；独立考试、补考和人工成绩发布继续使用 `/api/exam/**`。
- 课程测验管理嵌入开课配置的具体开课详情，按 `courseOfferingId + courseItemId` 选择内容项；题库练习不提供阅卷人配置。

## 前端接口

- 课程 API 类型、请求和 Mock 从 `course.openapi.json` 生成式对齐：移除课程 `examId`，使用课程考核 Attempt、阅卷人、批阅任务及结果接口。
- 独立考试 API 类型、请求和 Mock 从 `exam.openapi.json` 对齐：删除 `contextType/contextId/reviewerUserId`，展示执行来源摘要。
- 题库选择器使用已发布且启用的题库分页接口；试卷选择器使用启用且存在已发布版本的试卷列表，并在选择后读取明确的已发布版本 ID。

## 学员交互

- `QUESTION_BANK` 显示“题库练习”；`QUIZ` 显示“课程测验”。开始、继续、查看结果均使用课程考核 Attempt。
- 课程考核的 `EFFECTIVE` 为自动生效，页面不得显示独立考试的人工发布文案。
- 独立考试列表只消费独立考试接口返回的数据，来源摘要仅用于展示和排查，不作为前端权限判断。
