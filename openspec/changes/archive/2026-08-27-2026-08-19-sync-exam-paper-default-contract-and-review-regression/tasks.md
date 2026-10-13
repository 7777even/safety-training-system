# Tasks：同步考试试卷默认规则契约并修复批阅回归

> 状态：Task 1～3 已完成

## Task 1：恢复考试策略配置与详情核查

- 状态：已完成
- Owner：Codex
- 输入：当前考试工作台基础信息组件、详情和发布核查页面。
- 输出：创建/草稿编辑始终显示考试策略；详情和发布核查展示同一份完整、中文翻译的策略摘要。
- 前后端影响：PC Web；不修改后端、接口、权限或数据结构。
- API 依赖：既有 `GET /api/exam/exams/{examId}` 的 `policy` 返回值。
- 数据影响：无。
- 验收标准：`showPolicy` 不再造成策略区隐藏；新建、草稿编辑、详情及发布核查均能看到策略字段；枚举均有中文文案。
- 测试方式：先添加策略显示断言并观察失败，再完成实现；执行目标前端测试、类型检查和轻量自审。

## Task 2：按试卷默认规则契约保存创建与草稿更新

- 状态：已完成
- Owner：Codex
- 输入：最新 `docs/api/exam.openapi.json`、考试请求类型、工作台表单、试卷版本详情查询和 Mock。
- 输出：创建、草稿未换卷、草稿换卷、草稿解绑四种场景按契约构造请求；界面只将用户修改的试卷规则作为覆盖提交，并在保存后回读生效值。
- 前后端影响：PC Web；不修改后端、接口、权限或数据结构。
- API 依赖：`POST /api/exam/exams`、`PUT /api/exam/exams/{examId}`、`GET /api/exam/exams/{examId}`、既有试卷版本详情查询。
- 数据影响：无新增数据；仅按既有接口保存考试行。
- 验收标准：绑定试卷创建可省略四项规则；有试卷时不提交独立及格分；无试卷或解绑时前端提示并要求五项规则；草稿可换卷/解绑；未改字段符合
  PUT 的省略语义；分类、编码、名称和封面不由前端从试卷复制；Mock 与类型匹配契约。
- 测试方式：先为四种请求构造场景添加失败断言，再实现；执行目标前端测试、类型检查、受影响 lint 和轻量自审。

## Task 3：修复作答详情合并回归并验证批阅意见

- 状态：已完成
- Owner：Codex
- 输入：`ExamAttemptServiceImpl.toVO`、批阅接口及 `ExamAttemptServiceImplTest`。
- 输出：作答详情可以正常组装；批阅意见保持提交、保存和管理端回显。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：既有 `POST /api/exam/answers/{examAnswerId}/review` 与 `GET /api/exam/attempts/{examAttemptId}`。
- 数据影响：无。
- 验收标准：不存在重复局部变量；`get_returnsLatestReviewComment` 通过；`ExamAttemptServiceImplTest` 全部通过。
- 测试方式：先执行既有单测确认失败，再以最小修复使其通过；执行
  `mvn -pl sts-service-admin -am test -Dtest=ExamAttemptServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`，并完成双轴
  Review。
