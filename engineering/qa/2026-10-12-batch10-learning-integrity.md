# 第十批（74 → 75 学习防挂机、互斥与随机水印）QA 记录

日期：2026-10-12　Change：`openspec/changes/2026-10-12-complete-batch10-learning-integrity`

## 范围与人工确认

- 2026-10-12 用户确认批次范围：74 → 75；保留防挂机、互斥、随机水印/验证题差异；强化防作弊本期延期，未确认重启不实施。
- 三项范围决策（均按推荐方案）：①74「同一账户多终端登录」按学习域互斥实施（单活跃学习会话 + 终端标识绑定检测），登录层不全局互踢，74 不勾选整项；②75 随机化平铺水印，不加漂浮弹幕；③验证题纯前端门控，无新后端接口。

## 实现摘要

| 项 | 内容 |
|---|---|
| T1 互斥（74） | `sts_course_study_session` 增加 `device_id` 与 `(user_id, update_time)`、`(device_id, update_time)` 索引；新 trace 落库前校验同一账户其他活跃上下文（5095）与同一终端其他账户（5096）；活跃窗口 90 秒；`endReason=HEARTBEAT` 保持活跃、非 HEARTBEAT 收尾立即释放；同 trace 幂等路径不重复校验 |
| T2 防挂机（74） | shared `learning-verification.ts`（60～180 秒随机间隔、60 秒时限、两位数以内三种算术）；PC ElDialog / H5 自绘弹层；弹窗暂停计时、答对恢复并重新调度、超时 `TIMEOUT` 结束会话并停止计时上报 |
| T3 随机水印（75） | shared `watermark.ts` 增加 `buildRandomWatermarkTile`/`nextWatermarkDelayMs`（单元尺寸、旋转角、字号、锚点随机）；学习页员工编号优先、45～90 秒随机重建；考试/作答页固定水印不变 |
| 契约 | `endReason` 增加 `HEARTBEAT`；`StudySessionReportRequest` 增加必填 `deviceId`；`course.openapi.json` 同步字段、枚举、示例与 5095/5096 |
| 数据 | migration `V20261012100000__add_study_session_mutex_fields.sql`；init `schema-course.sql`、init `schema-version` 与 `AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION` 同步至 20261012100000 |

## 自动化验证

| 验证 | 结果 |
|---|---|
| 后端 `CourseLearningServiceImplTest` | 36/36 通过（新增 5095 拒绝、5096 拒绝、deviceId 落库 3 项；强化同 trace 幂等不触发互斥断言） |
| 后端课程域单测（默认 profile，IT 除外） | 全部通过；`CourseLearningControllerWebSliceTest` 10/10（载荷同步 deviceId） |
| 后端全量单测（模块） | 1347 项：2 失败/15 错误与 HEAD 基线 worktree 完全一致（ffmpeg/qpdf 环境缺失、`ExamControllerTest` 存量 1 项），零新增 |
| 前端新增定向（node --test） | 11/11 通过（PC 5 + H5 5 + shared 工具真实函数用例：题目正确性、减法非负、乘法范围、间隔边界、水印随机与转义） |
| 前端存量课程学习测试 | 45/49；4 项失败与 HEAD 基线一致（移动 AssetPreview 2 项、课程作答 1 项、PC 附件文案 1 项，均为存量，零新增） |
| 两端 typecheck | PC / 移动端 `vue-tsc --noEmit` 均通过 |
| 目标 lint | 移动端零警告；PC 学习页仅存量 `<img/>`、`<input/>` 自闭合警告（HEAD 已存在，未修改这些行） |
| 移动端 H5 build | `DONE Build complete.` |
| OpenSpec strict / OpenAPI JSON / git diff --check | 通过 |

## 阻塞与未覆盖（如实登记）

- **真实 SQL IT 未执行**：`CourseStudySessionMapperIT` 已补互斥查询用例（其他上下文活跃/显式收尾/心跳过期、同终端其他账户/同账户/过期）并编译通过；Docker daemon 在线但 Docker Hub 不可达（`registry-1.docker.io` 超时），本地无 `mysql:8.0.45`，IT 无法启动。历史上批次同样受此阻塞。
- **75 真实验收已执行（2026-10-12，PC + H5 各 8/8 通过）**：见下方「真实验收」节，75 两端通过、整项收口；74 的互斥冲突提示未做真实验收（登录层互踢为遗留，74 保持未勾选）。
- **web 终端标识局限**：`deviceId` 为客户端持久化随机值，清除存储或隐私模式可重新生成，属尽力而为检测，不宣称为安全边界（design 已记录）。
- **账户切换等待**：页面关闭后旧会话最多 90 秒才释放，同设备换账户学习在窗口内会被 5096 拒绝，属该模型已知取舍，未新增释放接口。
- **同终端同内容双标签页**：上下文键相同不互斥，原文三条禁止语义未覆盖该形态，登记为已知边界。
- **竞态**：两个新上下文同时首次上报可能同时通过互斥校验；败方下一次心跳被 5095 拒绝并停止，自愈，不影响数据正确性。
- **存量失败**：PC/H5 回归 4 项与 HEAD 一致（AssetPreview、课程作答、附件文案），本批未修改对应文件，不代为修复。

## 真实验收（2026-10-12，功能项 75 随机水印与验证题）

环境：PC dev `localhost:5173` 与移动端 H5 dev `localhost:5173`（hash 路由）均代理远端后端 `19.191.192.146:20081`；登录账号 `DEMO/superAdmin`，使用其已报名课程 enrollment 8「测试课程」（`isWatermarkEnabled=1`，含可学习 ASSET 内容项）。浏览器：Playwright 驱动系统 Edge（headless；移动端视口 390×844）。**可控复现说明**：为在单次运行内观察基于真实随机间隔的行为，临时将 shared `learning-verification.ts`（调度间隔 60～180s→3～6s、作答时限 60s→12s）与 `watermark.ts`（重建间隔 45～90s→3～6s）调小，跑完已原样还原（两文件 `git diff` 为空）。

| # | 75 原文要求 | PC | H5 |
|---|---|---|---|
| 1 | 学习过程出现员工编号水印 | ✅ `.learning-page` 背景渲染 | ✅ `.directory-card` 背景渲染 |
| 2 | 水印文字=员工编号 | ✅ `superAdmin`(userCode) | ✅ `superAdmin`(userCode) |
| 3 | 不定时不定位置 | ✅ tile 随机重建 | ✅ tile 随机重建 |
| 4 | 随机弹出验证题 | ✅ | ✅ |
| 5 | 防挂机门控（须作答） | ✅ 模态无关闭按钮 | ✅ 覆盖层须作答 |
| 6 | 答对恢复计时 | ✅ | ✅ |
| 7 | 超时按挂机结束并提示 | ✅ | ✅ |

两端各 8/8 通过。证据（截图 + JSON）在仓库外 `d:/gkproject/sts-acceptance/`：PC `accept-01~05-*.png` / `evidence.json`；H5 `h5-01~06-*.png` / `h5-evidence.json`。「弹窗暂停计时」由代码门控（`syncStudyClock` 受 `!verificationVisible` 控制）+ 单测覆盖，UI 侧以"模态/覆盖层不可关闭、必须作答"佐证。门户首页 `/api/learner/home/tasks` 的 500 为超管无学员上下文的看板接口，与学习页/75 无关。**74 未做真实验收**：互斥冲突提示（5095/5096）需双登录态联调，本期未执行；74 登录层互踢为遗留，74 保持未勾选。

## 遗留决策

- 74「同一账户多终端登录」的登录层全局互踢（Sa-Token `is-concurrent`）本期不实施，需单独人工确认后另立 Change；在此之前 74 保持未勾选。
- 考试侧强化防作弊（切屏次数、分屏/复制/调试限制、无操作强制交卷、最短答题时长等 ExamPolicy 延期列）不重启。
