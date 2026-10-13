# Design：课程配置与课程编排（草稿态）实施设计

## 1. 依据与边界

- 表结构唯一依据：`../2026-07-21-design-safety-training-mysql-schema/ddl/02_asset_course.sql`（已评审冻结）。本 Change
  不修改任何表结构；若实施中发现必须改表，须回到数据模型 Change 走变更。
- 步进依据：`docs/requirement/course-module-task-plan.md` 步骤 2、3。
- 本 Change 交付：正式 migration、后端 Entity/Mapper/Service/API、PC 管理端课程制作页面、契约与集成测试。

## 2. 依赖边界（实施前必须确认）

### 2.1 最小机构支撑（已确认，2026-07-21，架构：采用方案 A）

课程所有聚合根引用 `organization_id`，但数据模型 Change 的 Task 1（机构、成员与公共业务支撑）尚未实施。

- **方案 A（已采用）**：本 Change 的第一个 migration 附带创建 `sts_org_organization`（按已评审 DDL 原样），并通过受控初始化写入一条默认机构（稳定
  `org_code=DEFAULT`，幂等，见 `V20260721100000__create_org_organization.sql`）；本 Change
  内所有课程数据归属该机构。机构管理、成员、参与范围、角色数据范围仍完整保留给 Task 1，届时无需迁移课程数据。
  `DefaultOrganizationService` 解析并缓存该默认机构 ID，供 `CourseCategoryServiceImpl`/`CourseSettingServiceImpl` 使用。
- 方案 B（未采用）：先完整交付数据模型 Task 1 再开工本 Change。周期长，但权限模型一步到位。

采用方案 A 的权限降级约束：本 Change 权限只做"功能权限（`us_sys_menu.perms` 新增课程权限编码）+ 机构隔离 + 创建人"三层；
`sts_common_resource_admin_permission` 资源管理员授权与六类 `data_scope` 在 Task 1 交付后统一接入，API
设计必须预留过滤条件位，不得让前端感知权限模型变化。
当前只有一个默认机构，"跨机构越权访问"场景暂无法端到端验证，需等 Task 1 交付多机构后补齐（见 tasks.md Task 1 已知缺口）。

### 2.1.1 模块落地方式（已确认，2026-07-21，架构）

本项目不拆微服务。课程域实现为 `com.sts.course.*` 独立顶级包（与 `com.sts.admin` 平级），并入 `sts-service-admin`
同一进程、同一部署单元：

- 不新建 Maven 模块/pom、`@SpringBootApplication` 启动类、端口或 Nacos 服务注册。
- `AdminApplication` 的 `@ComponentScan` 与 `@MapperScan` 同时覆盖 `com.sts.admin` 与 `com.sts.course`。
- 跨切面基础设施（`HttpApiExceptionHandler`、`ResultEasyTransAdvice` 的 `basePackages`；Sa-Token 拦截路径 `/**`
  ；MyBatis-Plus 分页/乐观锁拦截器；审计字段自动填充）直接复用 `com.sts.admin.config.*` 现有 Bean，不重复实现。
- 课程域自有的 `Entity`/`Mapper` 位于 `com.sts.course.entity`/`com.sts.course.mapper`（含本域独立的 `AppBaseMapper`
  ，避免反向依赖 `com.sts.admin.mapper` 造成跨域耦合）；`Service`/`Controller`/`Converter`/`DTO`/`VO` 分别在
  `com.sts.course.{service,controller,converter,dto,vo}`，结构对齐 `com.sts.admin.system.*` 现有约定。
- 详见 `docs/architecture/backend-module-boundary.md`、`docs/architecture/backend-module-layout.md` 的对应更新。

### 2.2 素材依赖（步骤 1）

`ASSET` 内容项、材料规则的 `aux_audio_asset_id`、课程封面 `cover_file_id`、可下载素材均引用素材/文件。素材上传与转码能力（步骤
1）须先行或并行交付；
本 Change 的 Task 3 在素材能力就绪前，只能以联调桩数据验证引用校验逻辑（引用存在、未删除、`process_status=SUCCEEDED`
），不得虚构素材实体交付验收。

### 2.3 题库/考试依赖（禁用预留）

`sts_course_item_knowledge`（引用题库模块知识点）、`sts_course_item_question_bank`、`sts_course_item_video_question`
（引用题目版本）三张表随 migration 建立但**本 Change 不提供任何写入能力**；
`item_type` 枚举中 `KNOWLEDGE_POINT/QUESTION_BANK/EXAM/PRACTICE` 在 API 层返回"暂未开放"错误。放开时机为步骤
6（题库/考试模块交付后）。

## 3. 落表与 migration 拆分

migration 命名遵循 `V<yyyyMMddHHmmss>__<description>.sql`，每个 migration 一个可说明变更，与 Task 对应：

| migration                                      | 表                                                                                                                                                                                                    | 对应 Task |
|------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `V20260721100000__create_org_organization.sql` | `sts_org_organization` + 默认机构初始化                                                                                                                                                                     | Task 1  |
| `V*__create_course_config_tables.sql`          | `sts_course_category`、`sts_course_setting`、`sts_course_course_category`                                                                                                                              | Task 1  |
| `V*__create_course_authoring_tables.sql`       | `sts_course_course`、`sts_course_chapter`                                                                                                                                                             | Task 2  |
| `V*__create_course_item_tables.sql`            | `sts_course_item`、`sts_course_item_material_rule`、`sts_course_download_asset`、`sts_course_live_session`、`sts_course_item_knowledge`、`sts_course_item_question_bank`、`sts_course_item_video_question` | Task 3  |

`sts_course_version`、`sts_course_offering` 等步骤 4/5 的表**不在本 Change 建立**，避免出现无能力覆盖的空表。

## 4. API Contract 清单（先于实现在 `docs/api/` 定义）

| Contract | 主要接口                                                           | Task |
|----------|----------------------------------------------------------------|------|
| 课程分类     | 分类树查询、新增、改名、移动、排序、停用（同层同名唯一冲突返回明确错误码）                          | 1    |
| 机构课程设置   | 查询本机构设置、更新（首次访问不存在时按系统默认值创建）                                   | 1    |
| 课程       | 创建、基本信息维护、列表（机构内分页/按分类/按名称）、详情、逻辑删除、分类关联维护                     | 2    |
| 章节       | 章节树查询、新增、改名、移动、排序、删除（有内容项时拒绝）、单/多章节切换                          | 2    |
| 内容项      | 按章节新增（仅 ASSET/LIVE）、标题/必修/最低学时维护、排序、跨章节移动、删除；材料规则读写；可下载素材清单维护  | 3    |
| 直播场次     | 随 LIVE 内容项创建/维护（名称、讲师、链接、起止时间）、状态流转（DRAFT/SCHEDULED/CANCELLED） | 3    |

每个 Contract 必须包含 URL、Method、请求、响应、错误码、权限编码和示例；本 Change 的权限编码清单在 Task 1 一并登记到
`us_sys_menu`。

## 5. 关键业务规则

- **草稿态语义**：本 Change 内课程 `publish_status` 恒为 `DRAFT`；发布接口不提供（步骤 4）。所有结构化表可自由编辑，无版本快照。
- **章节树（已确认，2026-07-22，产品）**：`parent_id + ancestors` 维护路径，但业务层强制章节 `parent_id` 只能为
  0（根级），不允许章节挂到另一章节下；表结构仍保留 `parent_id + ancestors` 的通用树能力，为未来可能的深度扩展留口子，不代表当前放开多级嵌套。同课程同层章节名唯一（
  `uk_course_parent_name_deleted`）；删除章节要求其下无内容项和子章节。
- **单/多章节切换（已确认，2026-07-22，产品）**：`content_mode` 切换到 SINGLE 时，所有内容项移动到唯一默认章节并保持相对排序，只改
  `chapter_id` 和 `sort_order`，不复制/不删除内容项；切换只允许在课程尚无任何开课报名时执行。本 Change（步骤 2/3）不建
  `sts_course_offering`，因此当前不存在可被引用的开课，该守卫暂时恒真通过；步骤 5（开课）引入 `sts_course_offering`
  后，必须在切换接口补上"存在有效开课时拒绝切换"的校验，不能遗漏。
- **内容项引用校验**：`ASSET` 项写入时校验素材存在、未删除、同机构、`process_status=SUCCEEDED`；`LIVE`
  项创建时同事务创建直播场次（1:1，`uk_course_item_deleted`）。
- **材料规则**：每个内容项至多一条（1:1）；未配置时按机构课程设置的默认值计算展示，读接口需给出"生效值 + 来源（默认/自定义）"。
- **删除保护**：素材被内容项或可下载清单引用时，素材侧删除必须被拦截（依赖 `idx_asset` 反向检查，接口归素材模块，本 Change
  提供引用查询）。
- **审计与逻辑删除**：全部遵循数据模型 Change 的 A/B 类审计模板；业务唯一键含 `deleted`。

## 6. 前端范围

- PC 管理端：课程分类管理、机构课程设置、课程列表与创建、课程制作页（章节树 + 内容项编排 + 材料规则 + 直播配置 + 可下载素材）。
- 学员端本 Change 无交付（课程对学员可见要等步骤 5 开课）。
- 前端仅消费 `docs/api/` Contract，不自行推导接口。

## 7. 测试设计

- migration：Testcontainers MySQL 空库按序执行，验证表结构与唯一键和已评审 DDL 一致。
- 单元：章节树操作（移动/排序/删除保护）、单/多章节切换、内容项类型开放度、材料规则默认值合并。
- 契约：全部 Contract 的正常/异常/权限用例；禁用类型返回明确错误码。
- 集成：机构隔离（跨机构 ID 访问返回无权限或不存在）、同层同名唯一并发写入、LIVE 项与场次同事务一致性。
- 数据：默认机构初始化幂等重跑不产生重复记录。

## 8. 待确认清单（进入对应 Task 前关闭）

| 事项                                       | 阻塞                  | 负责人   | 状态                                                                                                                                                                                    |
|------------------------------------------|---------------------|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 最小机构支撑采用方案 A 或先做 Task 1                  | Task 1              | 架构    | 已确认（2026-07-21，方案 A）                                                                                                                                                                  |
| 模块落地：并入 sts-service-admin 还是独立微服务        | Task 1              | 架构    | 已确认（2026-07-21，并入 `com.sts.course.*`，不拆微服务）                                                                                                                                           |
| 课程目录无限级保留、单/多章节切换规则                      | Task 2              | 产品    | 已确认（2026-07-22）：表结构保留无限级能力，业务层强制章节只能挂根级（两层：课程→章节→内容项）；切换仅允许无开课引用时执行，开课表未建期间守卫恒真，步骤 5 引入开课后必须补上真实校验                                                                                    |
| 直播提供方、链接访问与有效性规则                         | Task 3 直播部分         | 产品/架构 | 已确认（2026-07-22，按推荐默认）：外链模式；`live_url` 必填且须 http/https；`end_time > start_time`；创建默认 `DRAFT`；允许 `DRAFT↔SCHEDULED`、`SCHEDULED→CANCELLED`；本阶段不做 `LIVING/ENDED` 自动流转；`audience_id` 可空且暂不校验 |
| 课程权限编码清单（菜单/按钮粒度）                        | Task 1              | 产品/权限 | 已实施：`course:category:{list,add,edit,delete}`、`course:setting:{view,edit}`，登记于 `menus.json` module 300；粒度是否需要细化仍可复核                                                                    |
| Task 1 的 `docs/api/` OpenAPI Contract 文档 | 无（后续任一 Task 消费方需要时） | AI    | 已补：`docs/api/course-config-authoring.openapi.json`（2026-07-22）                                                                                                                        |
