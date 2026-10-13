# Spec Delta：课程模块 PC 管理端前端

## 新增交付要求

- 系统必须为已确认的课程素材、课程编排、发布版本、开课报名和学习管理能力提供 PC 管理端操作界面。
- 页面请求字段、状态值、权限点和错误语义必须来自 `docs/api/asset.openapi.json` 与 `docs/api/course.openapi.json`。
- 课程管理页面必须遵循已确认的 Figma 页面、Design System 和 `docs/design/`；没有设计或 Contract 支撑的入口不得以占位业务功能交付。
- 所有带 `version` 的更新必须执行乐观锁语义；冲突时提示刷新，不得静默覆盖。
- 前端权限只控制菜单和操作入口展示，后端继续承担真实鉴权和数据范围控制。

## FR 与页面映射

| FR | PC 管理端页面 | Contract |
| --- | --- | --- |
| FR-ENT-CRS-001～002 | 素材库、转码任务 | `asset.openapi.json` |
| FR-ENT-CRS-003～004 | 分类与设置、课程中心、课程编辑器 | `course.openapi.json` 分类/设置/课程/章节 |
| FR-ENT-CRS-005 | 课程编辑器 | 内容项、材料规则、直播场次、下载素材 |
| FR-ENT-CRS-006～008 | 版本页、开课管理、报名名单 | 发布版本、开课、报名 |
| FR-ENT-CRS-009 | 管理员学习详情 | 管理端学习详情 |
| FR-ENT-CRS-010 | 课程编辑器、开课管理 | 直播场次、开课 |

## 保持不变

- 不新增或改变业务状态机、权限模型、数据库结构或 API Contract。
- 学员端课程发现与学习能力继续等待对应 Contract Change。
- `KNOWLEDGE_POINT`、`QUESTION_BANK`、`EXAM`、`PRACTICE` 内容项继续禁用。

