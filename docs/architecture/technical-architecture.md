# 技术架构

## 工程形态

系统采用 Monorepo，前端、后端、长期知识库和 OpenSpec 规格在同一仓库内协同演进。

| 区域                                                           | 技术或职责                                   | 说明                                                                                                                       |
|--------------------------------------------------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `frontend/pc-web/`                                           | Vite + Vue 3 + TypeScript               | 承载门户、用户中心与管理后台的 PC Web 基础工程。                                                                                             |
| `frontend/mobile-app/`                                       | uni-app + Vue 3 + TypeScript + Vite     | 面向移动 H5、App、企业微信入口预留的移动端基础工程。                                                                                            |
| `frontend/shared/`                                           | 公共类型与工具                                 | 仅沉淀跨端稳定复用内容，不承载业务模块实现。                                                                                                   |
| `backend/`                                                   | Spring Boot 3.4.13 + JDK 21 + Maven 多模块 | `sts-common` / `sts-service-admin` / `sts-service-admin-init`；详见 [backend-module-layout.md](./backend-module-layout.md)。 |
| `backend/sts-service-admin-init/src/main/resources/schema/`  | MySQL 最新完整结构                            | 按模块拆分，由 init 首次建库。                                                                                                       |
| `backend/sts-service-admin/src/main/resources/db/migration/` | MySQL 增量                                | Admin 启动时由 Flyway 执行 `V`。                                                                                                |
| `docs/`                                                      | 长期知识库                                   | 保存架构、需求、API、部署等跨版本知识。                                                                                                    |
| `openspec/`                                                  | 唯一业务规格来源                                | 保存业务变更的 proposal、design、tasks 和 spec-delta。                                                                              |

## 协作架构

```text
OpenSpec 确认
  → docs/api 定义 API Contract
  → 前端 Mock First 与后端并行实现
  → 联调与 testHarness 验证
  → QA / Ship / Retro 过程记录
```

## 关键约束

- API Contract 以 `docs/api/` 为唯一协作边界；前端不得自行定义接口。
- 前端只控制展示，真实权限必须由后端校验。
- 所有数据库变更必须通过 `backend/sts-service-admin/src/main/resources/db/migration/`，禁止直接操作生产数据库。
- 后端接口、数据库结构、身份认证方式及外部集成细节，须在对应 OpenSpec 中确认。
- 直播、仿真、企业微信、App 原生能力的集成方式均为“暂定”。
