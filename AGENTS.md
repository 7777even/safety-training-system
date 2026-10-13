# 安全教育培训系统协作规则

本文件只保留跨目录、流程和安全边界；前后端细则分别见：

- `frontend/AGENTS.md`、`frontend/pc-web/AGENTS.md`
- `backend/AGENTS.md`
- `docs/architecture/`、`docs/api/`

## 1. 规则优先级

平台与人工指令 > 当前目录及父目录 `AGENTS.md` > 已确认 OpenSpec 与 `docs/api` > 当前 Task > 技能方法。

规则冲突时停止并报告，不自行猜测、合并或扩大范围。

## 2. 风险分级与最小流程

先按实际影响分级，再选择最低必要流程：

| 级别 | 适用范围 | 最低流程 |
|---|---|---|
| L0 | 问答、解释、翻译、只读检查、评审、文本润色 | 直接回答；不改文件、不建规格、不启用子 Agent |
| L1 | 不改业务/API/权限/数据库，且不超过 3 个文件的明确可逆小修 | 说明范围 → 修改 → 定向验证 |
| L2 | 依赖、构建配置、脚手架、非业务技术债 | 说明影响 → 修改 → 受影响 workspace 验证 |
| L3 | 课程、考试、培训、成绩、档案、证书、统计、预警及相关业务页面/API | OpenSpec → 人工确认 → TDD 实现 → 定向测试 → Review → QA/Retro |
| L4 | API 契约、权限模型、数据库、架构、生产部署或跨端协议 | 按 L3，并在实施前取得人工确认；数据库同步 migration |

L0/L1/L2 默认不创建 OpenSpec、QA、Ship、Retro，不启用子 Agent，不做无关全量验证。需求明确、可逆且无业务语义变化的小修不得升级为 L3。

L3/L4 一次只实施一个已确认 Task，最多连续三个；遇到需求、API、权限、数据库、架构或连续测试失败立即暂停。

## 3. L3/L4 开发闭环

1. 开始前读取路线图、进度表和当前已确认 Change；路线图只规定依赖顺序，不授权提前开发。
2. `openspec/` 是唯一业务规格来源。Change 必须包含 `proposal.md`、`design.md`、`tasks.md`、`spec-delta.md`，并先取得人工确认。
3. Task 按业务能力拆分，必须写 Owner、输入、输出、前后端影响、API 依赖、数据影响、验收标准和测试方式；禁止把数据库、接口或页面单独当业务 Task。
4. 新增行为、业务逻辑或缺陷修复按 Red → Green → Refactor：先让目标测试失败，再做最小实现，最后只重构本次产生的重复。
5. 生产代码的属性、方法和组件添加必要中文注释；只实现当前 Task，不顺带重构，不提前实现未确认能力。Mock、桩服务、占位页面和单层代码不算业务完成。
6. 完成后更新 `tasks.md`，再更新进度表及必要的 API、QA、Retro 记录。阶段关闭必须同时满足真实端到端验收、测试、Review 和文档同步。
7. L3/L4 需独立完成规格符合性和代码质量双轴 Review；L1/L2 做轻量自审。业务规则、权限、API、数据库或架构疑问标记为“需人工决策”。

## 4. 规格与契约

- 功能原始范围见 `docs/product/功能项.csv`；它不是实施授权，实施授权只来自已确认 OpenSpec。
- 业务实现必须完整覆盖已纳入功能项，不得因现有页面、枚举、表或 Mock 而缩减语义；延期项不得提前设计或作为当前阻塞。
- L3/L4 的 `proposal.md` 列出覆盖的功能项编号和原文语义；`design.md`、`tasks.md` 与验收标准可反向追溯。
- 对外 API 以 `docs/api/*.openapi.json` 为唯一机器可读契约。新增或修改接口、字段、错误码、权限或废弃语义时，必须同步 OpenSpec、OpenAPI、Mock 和调用方。
- OpenAPI 必须有 Controller 分组、接口 `summary/description`、字段中文 `description`、请求/响应 example、URL、Method、错误码和 `x-permission`；详细写法见 `docs/architecture/openapi-contract-writing-guide.md`。
- 前端不得自行设计接口、字段或权限；后端必须真实校验认证和授权。

## 5. 验证策略

- 文档或规则：`git diff --check`。
- 单文件前端或样式：受影响 workspace 的定向 lint 或 typecheck。
- 前端依赖、构建配置或 Vite：只构建受影响端；不默认同时构建 PC 和移动端。
- 后端：只运行受影响模块的定向编译/测试。
- L3/L4：按当前 Task 验收标准执行完整的受影响验证；不得用无关全量命令替代目标测试。
- 不要为了形式化验证连续执行全端 lint、typecheck、PC build、mobile build；不要无理由 `clean` 或重复构建。
- Admin 两层测试、Testcontainers、Mapper/Controller/Service 测试边界和命令详见 `backend/AGENTS.md` 与 `docs/architecture/test-strategy.md`。涉及真实 SQL、索引、Entity、Mapper、事务或乐观锁时必须补相应 IT；Docker 不可用要如实报告。

## 6. 目录职责

- `frontend/`：前端应用；依赖、组件、API 和端规则见子目录 `AGENTS.md`。
- `backend/`：Spring Boot 服务；分层、配置、权限和测试规则见 `backend/AGENTS.md`。
- `docs/`：长期知识、路线图、API 契约和架构说明。
- `openspec/`：唯一变更规格来源。
- `engineering/`：QA、Ship、Retro 和进度记录，不创建第二套业务 Task。

数据库资产职责：

- `backend/sts-service-admin-init/src/main/resources/schema/` 保存按模块拆分的最新完整 init SQL。
- `backend/sts-service-admin/src/main/resources/db/migration/` 保存不可改名、不可删除的 Admin 增量 migration。
- 表、索引、字段、约束或正式初始化数据变化时，必须同步 init 快照、增量 migration、`sts.init.schema-version`、Entity、API 和 OpenSpec design；禁止直接修改生产数据库。

## 7. 前后端协作边界

- 前端统一使用 pnpm workspace；禁止在 `frontend/` 混用 npm/yarn。锁文件为 `frontend/pnpm-lock.yaml`，发现其他锁文件先确认再删除。
- 跨包依赖使用 `workspace:*`；`shared` 只放至少两个端确实需要且语义一致、来源于已确认契约的纯类型/工具。
- Portal、User Center、Admin Console 的视觉和路由边界遵循 `frontend/pc-web/AGENTS.md`，不得把后台工作台结构复制到门户。
- 复杂缺陷按“复现 → 最小化 → 假设 → 验证 → 修复 → 回归”；只在确有复杂度或性能风险时使用诊断/设计类技能。
- 技能只作为工程辅助，不得生成 `openspec/` 之外的平行规格、任务或 Review 流程；用户明确指定的技能仍按其说明执行。

## 8. 权限、安全与 Git

AI 可以分析、设计、编码、测试、Review 和写文档；开发人员负责需求确认、架构决策、权限确认和最终合并。

AI 禁止：操作生产环境、修改生产数据、自定义业务规则、擅自创建权限模型、擅自改变数据库结构、未确认引入依赖。

Git 遵循 Git Flow：长期分支为 `main`/`develop`，功能分支默认 `feature/<business-capability>/<business>`；提交使用 `feat:`、`fix:`、`refactor:`、`docs:`、`test:` 等前缀。修改前保留用户已有工作树变更，不使用破坏性 reset/checkout。

当 `git config user.name` 为 `lims` 时，只修改 `backend/`、`docs/api/` 和后端相关 OpenSpec/工程记录；不得修改前端，除非用户当场明确授权。其余身份遵守通用前后端规则。

## 9. 项目路线与延期边界

长期路线唯一来源为 `docs/requirement/end-to-end-development-roadmap.md`，当前进度见 `engineering/plans/end-to-end-development-progress-tracker.md`。未形成真实纵向闭环前，不为后续阶段铺空页面、空表或占位能力。

仿真软件/教学/练习/考试、防作弊和人脸识别本期延期；素材阶段 1～9 只推进基础 CRUD，真实转码、派生文件和媒体预览待 M5 后另建并确认独立 Change。不得把延期项作为当前实施或阻塞。

## 10. 交付标准

L0/L1/L2：范围符合请求，定向验证通过，差异可解释。

L3/L4：OpenSpec 已确认，代码和测试完成，API/数据库/文档同步，QA/Retro 完成，双轴 Review 通过，未解决事项和真实联调缺口明确列出。
