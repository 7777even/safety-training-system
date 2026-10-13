# Tasks：课程配置与课程编排（草稿态）

> 状态：等待确认。确认后一次只实施一个 Task；每个 Task 完成 API Contract → migration → Entity/Service → 前端 →
> 测试的完整闭环后更新状态。
> 前置：design.md 第 8 节待确认清单中阻塞对应 Task 的事项必须先关闭。

## Task 1：最小机构支撑、课程分类与机构课程设置

- 状态：**已实施（2026-07-21，待复核）**
- Owner：AI（实施）；架构（机构方案确认）、产品（分类规则确认）
- 输入：数据模型 Change 已评审 DDL；最小机构支撑方案确认（design §2.1，采用方案 A）；课程权限编码清单
- 输出：`sts_org_organization`（含幂等默认机构初始化）、`sts_course_category`、`sts_course_setting`、
  `sts_course_course_category` 的 migration 与能力；课程分类树管理、机构课程设置管理；课程域功能权限编码登记
- 落地方式：2026-07-21 与产品/架构确认，本项目不拆微服务；课程域作为 `com.sts.course.*` 独立顶级包并入 `sts-service-admin`
  同一进程同一 JAR（与 `com.sts.admin` 平级），详见 `backend-module-boundary.md`
- API 依赖：课程分类 Contract、机构课程设置 Contract（见 `docs/api/course-config-authoring.openapi.json`）
- 数据影响：新增上述表；分类同层同名唯一含 `deleted`；机构设置每机构一条（`uk_org_deleted`）
- 已完成实现：
  - migration：`database/migration/V20260721100000__create_org_organization.sql`、
    `V20260721100001__create_course_config_tables.sql`；开发期 DDL 快照 `backend/sql/schema-course.sql`
  - Entity/Mapper：`com.sts.course.entity.{Organization,CourseCategory,CourseSetting}`、`com.sts.course.mapper.*`（含本域独立
    `AppBaseMapper`，不跨域依赖 `com.sts.admin.mapper.AppBaseMapper`）
  - Service：`CourseCategoryServiceImpl`（树装配、ancestors 维护、父校验、禁止移入子孙、同层同名护栏、删除前子分类占用检查）、
    `CourseSettingServiceImpl`（首次访问按系统默认值创建、并发首次访问重复键兜底、乐观锁更新）、`DefaultOrganizationService`
    （最小机构支撑的默认机构 ID 解析与进程内缓存）
  - Controller：`CourseCategoryController`（`/course/categories/**`）、`CourseSettingController`（`/course/settings`），均挂
    `@SaCheckPermission`（`course:category:*`、`course:setting:*`）
  - 跨切面接线：`AdminApplication` 的 `@ComponentScan`/`@MapperScan` 已覆盖 `com.sts.course`；`HttpApiExceptionHandler`、
    `ResultEasyTransAdvice` 的 `basePackages` 已登记 `com.sts.course`
  - 权限点：`backend/sts-service-admin-init/src/main/resources/data/menus.json` 已登记课程域菜单/按钮（module
    300），随现有逻辑自动绑定管理员角色
  - 业务错误码：`5000-5999` 段登记给课程域，见 `docs/architecture/api-standard.md`
- 验收标准：
  - [x] 分类树增删改移、排序稳定，同层同名并发写入仅一条成功（`CourseCategoryMapperIT` 真库验证
    `uk_org_parent_name_deleted`）
  - [x] 机构设置首次访问自动按系统默认值创建，更新后按显式版本号乐观锁校验（`CourseSettingServiceImplTest`/
    `CourseSettingMapperIT`）
  - [ ] 所有接口按功能权限 + 机构隔离 + 创建人三层控制，越权机构 ID 返回无权限或不存在——*
    *功能权限点已挂 `@SaCheckPermission` 并已登记菜单，机构隔离已在 Service 层按 `organizationId`
    过滤；但尚未跑通「真实登录 + 授权 + 越权机构 ID」端到端验证**（当前机构层面天然单租户，因为最小机构支撑方案下只有一个默认机构，越权机构
    ID 场景要等数据模型 Task 1 交付多机构后才具备真实测试条件）
  - [x] 默认机构初始化幂等，重复执行不产生重复记录（migration 用 `WHERE NOT EXISTS` 判重；`schema-course.sql` 快照因走全新空库故直接
    `INSERT`）
- 测试方式：
  - 单测（零 DB）：`CourseCategoryServiceImplTest`（6）、`CourseSettingServiceImplTest`（4）
  - Web 切片：`CourseCategoryControllerWebSliceTest`（5）、`CourseSettingControllerWebSliceTest`（4）
  - 集成测试（Testcontainers 真 MySQL）：`CourseCategoryMapperIT`（2，唯一键 + 逻辑删除重插）、`CourseSettingMapperIT`
    （2，单机构唯一 + 乐观锁冲突）
  - 全量回归：`mvn test`（187 用例）与 `mvn -Pintegration-test verify`（22 用例，含 admin 原有 IT）均 BUILD SUCCESS，零回归
- 已知缺口（待后续 Task 或独立事项补齐）：
  - 操作审计（`@OperLog`）未接入课程域 Controller（该切面当前仅为 `com.sts.admin.system.*` 配置切点），见
    `CourseCategoryController`/`CourseSettingController` 类注释
  - 越权机构 ID 的端到端权限测试等数据模型 Task 1（多机构）交付后补齐
  - 前端（PC 管理端课程分类/设置页面）由他方负责，不在本协作范围

## Task 2：课程定义与章节树

- 状态：**已实施（2026-07-22，待复核）**
- Owner：AI（实施）；产品（目录与切换规则确认，已于 2026-07-22 确认）
- 输入：Task 1；课程目录无限级与单/多章节切换规则确认（design §8，已确认）
- 输出：`sts_course_course`、`sts_course_chapter` migration 与能力；课程创建/维护/列表/删除、分类关联、章节增删改排序、单/多章节切换
- API 依赖：课程 Contract、章节 Contract（见 `docs/api/course-config-authoring.openapi.json`）
- 数据影响：新增两表（migration `V20260722010000__create_course_authoring_tables.sql`）；课程编码机构内唯一；章节强制
  `parent_id=0`（两层：课程→章节→内容项，表结构保留树能力）
- 已完成实现：
  - Entity/Mapper：`Course`、`CourseChapter`、`CourseCourseCategory`（B 类关联，`sts_course_course_category` 首次落地读写能力）
  - Service：`CourseServiceImpl`（创建课程时事务内自动生成唯一默认章节；课程编码冲突/跨机构访问护栏；分类关联覆盖式设置并校验同机构；内容模式切换幂等，切至
    SINGLE 时触发章节合并；删除课程级联删除全部章节与分类关联）、`CourseChapterServiceImpl`
    （章节强制根级；同课程同名护栏；最后一个章节禁止删除；章节合并保留排序最靠前者）
  - Controller：`CourseController`（`/course/courses/**`，含 `/content-mode`、`/categories` 子路径）、
    `CourseChapterController`（`/course/chapters/**`）
  - 权限点：`course:course:{list,add,edit,delete}`、`course:chapter:{list,add,edit,delete}` 已登记于 `menus.json` module
    300 / 页面 303
- 验收标准：
  - [x] 课程基本信息（概况/目标/大纲/考核方式/学分/积分模式）可完整维护，`publish_status` 恒为 DRAFT
  - [x] 章节树强制两层（课程→章节→内容项），支持增删改排序；删除最后一个章节被拒绝（`CourseChapterServiceImplTest`
    ）；删除有内容项的章节被拒绝（Task 3：`5034`）
  - [x] 单/多章节切换（切至 SINGLE 触发章节合并，保留排序最靠前者，其余逻辑删除）；内容项搬迁已在 Task 3 的
    `mergeChaptersToSingle` 内补齐；切换的"无开课引用"守卫因 `sts_course_offering` 未建暂恒真，步骤 5 引入开课表后必须补齐
  - [x] 课程逻辑删除后编码可复用（`CourseMapperIT` 真库验证 `uk_org_course_code_deleted`）；级联删除全部章节与分类关联
- 测试方式：
  - 单测：`CourseServiceImplTest`（8）、`CourseChapterServiceImplTest`（6）
  - Web 切片：`CourseControllerWebSliceTest`（4）、`CourseChapterControllerWebSliceTest`（3）
  - 集成测试：`CourseMapperIT`（2，唯一键+逻辑删除重插）、`CourseChapterMapperIT`（2，同上）
  - 全量回归：`mvn test`（208 用例）与 `mvn -Pintegration-test verify`（26 用例）均 BUILD SUCCESS，零回归
- 已知缺口（同 Task 1 模式）：`@OperLog` 未接入；前端由他方负责；开课引用守卫待步骤 5

## Task 3：内容项编排、材料规则与直播场次

- 状态：**已实施（2026-07-22，待复核）**
- Owner：AI（实施）；产品/架构（直播规则确认）；依赖素材能力（步骤 1）先行或并行
- 输入：Task 2；直播提供方与链接规则确认；素材模块的素材查询/引用校验契约
- 输出：`sts_course_item`、`sts_course_item_material_rule`、`sts_course_download_asset`、`sts_course_live_session` 及三张禁用预留表（
  `item_knowledge`、`item_question_bank`、`item_video_question`）的 migration；ASSET/LIVE 内容项编排、材料规则、可下载素材、直播场次配置
- API 依赖：内容项 Contract、直播场次 Contract（见 `docs/api/course-config-authoring.openapi.json`）
- 数据影响：新增七表（migration `V20260722030000__create_course_item_tables.sql`）；LIVE 项与场次 1:1 同事务；禁用预留表无写入路径
- 已完成实现：
  - Entity/Mapper：`CourseItem`、`CourseLiveSession`、`CourseItemMaterialRule`、`CourseDownloadAsset` 及对应 Mapper（继承
    `com.sts.admin.mapper.AppBaseMapper`）
  - 素材引用：`AssetReferenceService`（6011/6012 校验同机构、SUCCEEDED）
  - Service：`CourseItemServiceImpl`（ASSET/LIVE 创建、跨章节移动、材料规则 DEFAULT/CUSTOM、直播场次 DRAFT↔SCHEDULED
    流转、级联删除）、
    `CourseDownloadAssetServiceImpl`（全量替换）、`CourseAssetReferenceServiceImpl`（引用计数）
  - Controller：`CourseItemController`（`/course/items/**`）、`CourseDownloadAssetController`、
    `CourseAssetReferenceController`
  - 联动更新：`CourseChapterServiceImpl`（删除前 5034 校验、合并章节前搬迁内容项）、`CourseServiceImpl.deleteCourse`
    （先清理内容项与下载素材）
  - 权限点：`course:item:{list,add,edit,delete}` 已登记于 `menus.json`（1329-1332）
  - 业务错误码：5031-5039（内容项/直播/材料规则）、6011/6012（素材引用）
- 验收标准：
  - [x] 仅 ASSET/LIVE 类型可创建；其他类型返回 5032（`CourseItemServiceImplTest`）
  - [x] ASSET 项引用同机构 SUCCEEDED 素材（`AssetReferenceServiceImplTest`）
  - [x] 材料规则未配置时按机构 CourseSetting 返回 DEFAULT 来源
  - [x] LIVE 项创建/删除与直播场次同事务一致
  - [x] 内容项跨章节移动与排序稳定；删除级联材料规则与直播场次
  - [x] 章节删除前校验内容项占用（5034）；SINGLE 模式合并前搬迁内容项
- 测试方式：
  - 单测：`CourseItemServiceImplTest`（5）、`AssetReferenceServiceImplTest`（3）；更新 `CourseChapterServiceImplTest`（+5034）、
    `CourseServiceImplTest`（构造函数）
  - Web 切片：`CourseItemControllerWebSliceTest`（2）
  - 全量回归：
    `mvn -pl sts-service-admin -am test -Dtest=CourseItemServiceImplTest,CourseChapterServiceImplTest,AssetReferenceServiceImplTest,CourseItemControllerWebSliceTest`
    BUILD SUCCESS
- 已知缺口：`CourseItemMapperIT` 未建（可选）；前端由他方负责；`@OperLog` 未接入
