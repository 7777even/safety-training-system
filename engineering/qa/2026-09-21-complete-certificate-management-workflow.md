# QA：补齐证书模板设计与签发规则管理闭环

日期：2026-09-21

负责人：lims

OpenSpec：`2026-09-21-complete-certificate-management-workflow` Task 1～2

范围：证书模板设计草稿/发布/渲染、签发规则详情/更新/统一来源候选、权限与 API 契约；不含前端和档案签到能力。

## 结论

通过。模板工作台所需的完整读取与草稿保存、真实底图样式渲染、安全删除，以及签发规则详情、更新和三类统一候选均已形成后端闭环。
`certificate:rule:edit` 已同步 Admin-init、Flyway migration 和 OpenAPI。

## 交付内容

| 能力      | 结果                                                                                       |
|---------|------------------------------------------------------------------------------------------|
| 模板详情与草稿 | GET 返回当前发布版和唯一草稿及完整元素；PUT 按乐观锁整体替换草稿设计，不修改已发布历史                                          |
| 模板发布与删除 | 发布原子归档旧版本并提升草稿；被有效规则引用时删除返回 409/9403                                                     |
| 证书渲染    | 从文件存储读取并解码真实底图，按坐标、字体、字号和颜色绘制 TEXT/VARIABLE                                              |
| 规则详情与更新 | GET 可可靠回显模板/来源名称、自动签发和版本；PUT 校验乐观锁、已发布模板及可绑定来源                                           |
| 统一候选    | 按 COURSE/EXAM/TRAINING 分页，只返回当前租户可绑定且状态合格的来源                                             |
| 权限与契约   | 新增 `certificate:rule:edit`，schema version 为 `20260921160000`；证书 OpenAPI 为 17 个 operation |

## TDD 与测试证据

- Red → Green：模板草稿替换的首次 MySQL IT 暴露逻辑删除列未按元素 ID 更新；改为显式 `deleted = template_element_id`
  后，重复保存与发布场景通过。
- 零数据库合并回归：`CertificateControllerTest`、`CertificateTemplateServiceImplTest`、`CertificateImageRendererTest`、
  `CertificateIssueRuleServiceImplTest`、`CertificateServiceImplTest`，18/18 通过。
- 证书 MySQL IT：`CertificateTemplateServiceIT`、`CertificateIssueRuleServiceIT`、`CertificateTenantBoundaryIT`、
  `CertificateMapperIT`，10/10 通过。
- 权限 migration 定向 IT：`FlywayMigrationIT#shouldAddCertificateRuleEditPermission`，1/1 通过，包含重复 migrate 幂等检查。
- 完整 `FlywayMigrationIT`：6/6 通过。已删除依赖不存在 target `20260911152000` 的执行归属升级夹具；学习规则收敛与旧快照升级在执行
  `V20260920190000` 前还原 `sts_course_setting` / `sts_course_item_material_rule`。
- Admin-init IT：`AdminInitFlywayIT`，1/1 通过，最新基线 `20260921160000` 和整体种子初始化成功。

## 契约与静态验证

1. `certificate.openapi.json` JSON 解析通过，operation 数为 17。
2. 17 个 operation 均包含 `CertificateController` tag、中文 `summary/description` 和 `x-permission`。
3. 模板发布版本号、草稿设计字段、TEXT/VARIABLE 元素枚举、规则更新模型、统一候选分页模型已纳入 Schema。
4. 本地 Schema/response `$ref` 悬空引用均为 0；所有 `docs/api/*.openapi.json` 均可解析。
5. `menus-back.json` 中 8513 权限节点的父级、权限码、类型和模块校验通过。
6. `openspec validate 2026-09-21-complete-certificate-management-workflow --strict` 与 `git diff --check` 通过。

## 已知基线问题

无。历史 `FlywayMigrationIT` 夹具已与当前 init / classpath 中的 V 文件对齐。

## 双轴 Review

- 规格符合性：通过。Task 1～2 的 API、权限、租户、状态、乐观锁和不可变历史语义均有实现及测试；未提前实现档案列表、个人档案或签到事实能力。
- 代码质量：通过。事务边界覆盖草稿替换/发布和规则更新；租户过滤复用拦截器并有 MySQL 边界测试；错误码、权限、OpenAPI 与运行时一致。

## 遗留

- 前端设计工作台和规则编辑页需另开前端 Change 消费当前 OpenAPI。
- 全部已结束培训档案、`/archive/persons/me` 与签到事实来源仍属于独立档案 Change。
