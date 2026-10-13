# QA：PC Web 培训接口分层适配

## 范围

OpenSpec Change：`2026-09-08-adapt-pc-training-api-layering`。

仅覆盖 PC Web 培训标准、模板、计划、课程/考试来源标识和学员培训详情入口；未修改后端、数据库、权限或移动端。
门户课程列表与培训详情的既有未提交视觉调整被保留，未被接口适配覆盖或重置。

## 自动化验证

- `node --test frontend/pc-web/tests/training-plan-api.test.mjs frontend/pc-web/tests/training-composition-linkage.test.mjs frontend/pc-web/tests/training-learner-api.test.mjs`
  → 31 项通过，0 失败。
- `pnpm --filter pc-web typecheck`
  → 通过。
- 定向 ESLint（培训 API、计划/模板工作台、课程/考试列表与两个门户页面）
  → 0 error；`portal/class/index.vue` 有 2 条既有 `vue/html-self-closing` warning，未自动格式化或改写。
- `pnpm --filter pc-web build -- --outDir .vite-training-api-verify`
  → 通过（独立输出目录）。
- `git diff --check`
  → 通过。

## 覆盖场景

1. 新建计划只提交 `sourceTemplateId`，由服务端复制课表；草稿覆盖调用 `apply-template`，不再读取模板详情后手工复制阶段。
2. 草稿外覆盖、无效模板和标准指定资源缺失分别映射 8158、8157、8156 的可处理中文提示。
3. 标准规则不再改写模板课表；模板未填执行配置可保存/发布，计划层仍校验本期执行配置。
4. 课程与考试列表均以 `planContentId` 显示独立对象或培训生成对象，未通过名称或其他字段猜测来源。
5. 学员培训详情保留统一 `enter` 返回 ID 跳转、`score != null`（含 0 分）、计划结束态和全部已定义内容状态的中文展示。
6. 模板 COURSE/EXAM 内容的 `resourceVersionId` 从详情回填、资源选择、类型切换到阶段替换请求完整透传；题库/线下不猜测版本。
7. 标准使用培训引用资源接口、线上占比按页面百分数与接口比例值转换；计划过程页读取独立事实接口，结果入口使用当前培训结果抽屉。

## 补齐后的回归

- 全部 `frontend/pc-web/tests/training-*.test.mjs`
  → 76 项通过，0 失败。
- 模板、标准、计划过程和执行面板受影响生产源码 ESLint
  → 通过。
- 测试文件直接纳入现有 ESLint 命令时，因仓库未配置 Node 的 `URL`/`Buffer` 全局而报 15 项 `no-undef`；这不是生产源码错误，未修改 ESLint 全局配置以扩大本 Change 范围。
- 独立目录 Vite 构建
  → 后台构建进程正常退出，生成 533 个 assets，标准输出与错误输出为空。

## 真实登录态待验收

当前会话未提供可登录的非生产环境、账号及完整测试数据，因此没有声称已完成真实接口验收。后续按以下链路执行：

1. 标准 → 模板（无执行配置）→ 带模板创建计划，确认服务端回填阶段内容。
2. 草稿更换模板确认覆盖；发布后重试，确认 8158 边界。
3. 配置课程、考试、题库、线下内容及执行参数，绑定人员后预检/发布/生成执行；故意缺少标准指定资源确认 8156。
4. 学员从“我的培训”分别进入课程、考试、题库和线下场次，核对 enter 返回 ID、完成状态、0 分/非零成绩与已结束展示。
