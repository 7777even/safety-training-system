# Tasks：参与范围、冻结名单与统一人员选择（BASE-03）

> 状态：已完成（2026-07-24）。依据已评审数据模型直接实施。

## Task 1：参与范围表与 schema 同步

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：已评审 `01_org_common.sql` 三表；BASE-02 已完成
- 输出：正式 migration；`schema-common.sql`；Entity/Mapper；IT 装载
- 前后端影响：仅后端/DB
- API 依赖：无
- 数据影响：新建 audience 三表
- 验收标准：
  - [x] 空库按 migration 可执行
  - [x] BOUND 唯一约束、规则唯一约束、成员唯一约束真库可验证（`AudienceMapperIT`）
  - [x] 软删后 BOUND 同键可复用
- 测试方式：Mapper IT

## Task 2：参与范围 CRUD、规则、解析/冻结与人员选择 API

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：Task 1；design
- 输出：`com.sts.audience.*`；OpenAPI；权限点；零 DB + IT；课程报名准入校验
- API 依赖：新建 `docs/api/common-audience.openapi.json`
- 数据影响：无新增表
- 验收标准：
  - [x] TEMPLATE/BOUND 创建与校验；规则整表替换
  - [x] EXCLUDE 优先于 INCLUDE；ALL/DEPT/TAG/POSITION/USER/AUDIENCE 可解析
  - [x] DYNAMIC 预览实时算；FROZEN 写入成员表后 contains 读成员
  - [x] 人员选择按机构+关键词/部门/标签/岗位分页
  - [x] 开课有 audienceId 时报名/指派校验 contains
  - [x] OpenAPI 按 Controller 分类，字段注释与 example 齐全
- 测试方式：`AudienceResolveServiceImplTest`、`AudienceControllerTest`、`AudienceMapperIT`；课程报名单测

## Task 3：进度与 QA 回填（本批次）

- 状态：已完成（2026-07-24）
- Owner：AI
- 输入：Task 1～2 通过
- 输出：更新进度表 BASE-03；QA 记录
- 验收标准：
  - [x] tasks.md 勾选；进度表状态/证据/下一步更新
  - [x] QA 记录命令与结果
