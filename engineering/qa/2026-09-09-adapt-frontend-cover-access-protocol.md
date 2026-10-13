# 前端封面受控访问契约 QA

日期：2026-09-09
Change：`2026-09-09-adapt-frontend-cover-access-protocol`
范围：PC Web、shared、移动端最小兼容；未修改后端、OpenAPI、数据库、权限或移动端交互设计。

## 契约核对

- 培训类型写入字段为 `coverFileId`，编辑预览使用 `GET /api/training/types/{trainingTypeId}/cover` 的 Blob 响应。
- 学员课程、考试、培训任务只消费服务端的 `coverUrl`；空值不请求，非空相对路径经既有请求层读取 Blob 后创建对象地址。
- 培训内容不从 `trainingTypeId` 或文件 ID 推导路径；课程、考试与培训入口边界未变。
- 培训引用考试仍直接消费 `training/reference-resources` 返回值，未增加前端 `planContentId` 来源猜测。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| 封面契约、培训类型 API、学员培训 API Node 测试 | 6/6 通过 |
| PC Web 封面契约与培训定向 Node 回归 | 79/79 通过 |
| `pnpm --filter shared typecheck` | 通过 |
| `pnpm --filter pc-web typecheck` | 通过 |
| `pnpm --filter mobile-app typecheck` | 通过 |
| 受影响 PC / 移动端源码 ESLint | 通过，无错误或告警 |
| PC Web 独立输出目录 Vite build | 通过，534 个产物；仅有既有依赖浏览器外置与 chunk 体积提示 |
| `git diff --check` | 通过 |

## 双轴审查

- 规格符合性：通过。字段、受控读取、学员响应模型与来源边界均与当前 OpenAPI 一致。
- 代码质量：初审发现移动端刷新未释放旧 Blob、封面测试断言不够精确、PC 重复 import；均已修正并复验。

## 待人工验收

真实环境已可登录，待按验收数据执行：管理员编辑培训类型封面回显；学员三类列表的有封面、无封面、无权限/无封面不泄漏文件 ID；培训详情统一进入课程、考试、题库和线下内容。

## Task 2.2 复开补齐（2026-09-09）

- 移动培训任务 API 已声明 `trainingTypeId`、`coverUrl`；移动“我的培训”按非空 `coverUrl` 通过带登录态 Blob 请求读取封面，空值或读取失败展示占位。
- 新增回归断言覆盖移动培训字段、空值分支、原样路径读取与 Blob 地址释放；先观察失败，再以最小实现转绿。
- `node --test frontend/pc-web/tests/cover-access-contract.test.mjs`、`pnpm --filter shared typecheck`、`pnpm --filter pc-web typecheck`、`pnpm --filter mobile-app typecheck` 及移动端受影响源码 ESLint 均通过。

## 非本次范围基线

宽泛学员考试静态测试中已有 5 项失败（`learner-exam-api` 1 项、`learner-exam-center` 4 项）。经 `f48514f` 基线核对，断言涉及的旧字符串在本次改动前已不存在，未在本 Change 中修改或掩盖。
