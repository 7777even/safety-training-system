# Tasks：培训标准 / 计划 / 执行后端（M3）

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：TRAIN-01 培训类型与标准

- 状态：已完成
- Owner：lims
- 输入：DDL `sts_training_type/standard/standard_rule`；BASE-04 监管已有
- 输出：migration（类型/标准表 + ≥25 类型 seed）、`com.sts.training` 类型/标准 API、零 DB + IT
- API：纳入 `docs/api/training.openapi.json`（可分批补全）
- 验收标准：类型 CRUD；标准+规则+发布；seed ≥25 且幂等；不新建监管表
- 测试方式：零 DB Service；Mapper IT
- [x] migration 类型/标准 + seed
- [x] Type/Standard Service/Controller
- [x] 零 DB + IT

## Task 2：TRAIN-02 培训模板

- 状态：已完成
- Owner：lims
- 输入：Task 1
- 输出：模板分类/模板/阶段/内容；publish
- 验收标准：内容类型无 LIVE；发布后可被计划引用；复制不回写源
- [x] template Entity/Service/Controller
- [x] stages/contents/publish
- [x] 测试

## Task 3：TRAIN-03 评价表单不可变版本

- 状态：已完成
- Owner：lims
- 输入：DDL evaluation_form*
- 输出：`com.sts.evaluation`；`evaluation-form.openapi.json`
- 验收标准：发布新版本只读旧版；含 items；score_rule 最小可配
- [x] migration evaluation form tables
- [x] Form Service/Controller + 测试

## Task 4：TRAIN-04 培训计划与参训人

- 状态：已完成
- Owner：lims
- 输入：Task 2～3；BASE-03 audience
- 输出：计划/阶段/内容/评价绑定/参训人/发布快照
- 验收标准：发布冻结快照与哈希；audience bind；跨机构资源 USE 校验
- [x] plan CRUD + publish snapshot
- [x] participants bind-audience/assign
- [x] 测试

## Task 5：TRAIN-05 执行实例生成

- 状态：已完成
- Owner：lims
- 输入：Task 4
- 输出：`sts_training_execution_link` + generate-executions 幂等；OFFLINE 建场次
- 验收标准：重复调用不重复建；COURSE/EXAM/OFFLINE 分支正确
- [x] execution_link migration
- [x] generateExecutions + 测试

## Task 6：TRAIN-06 签到 / 线下成绩 / 附件 / 奖惩 / 教师任务

- 状态：已完成
- Owner：lims
- 输入：Task 5
- 输出：attendance、offline-score、attachment、reward-penalty、teacher-tasks API
- 验收标准：签到唯一；线下成绩唯一键；教师任务按 lecturer 聚合
- [x] 上述 API + 零 DB 测试

## Task 7：TRAIN-07 OpenAPI / 权限 / 进度 / QA

- 状态：已完成
- Owner：lims
- 输出：完整 OpenAPI；menus；进度表 M3/TRAIN-* 关闭；QA
- 验收标准：契约含字段说明与 example；M3 已完成（lims 后端口径）
- [x] training.openapi.json + evaluation-form.openapi.json
- [x] menus + docs 索引
- [x] 进度表 + QA
- [x] 目标测试通过
