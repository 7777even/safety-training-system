# 已确认纵向 Task

每次只执行一个 Task，每阶段最多连续三个。勾选仅代表代码交付，真实验收另记。

|状态|Task/功能项|输入|输出及前后端影响|API/数据影响|验收与测试|
|---|---|---|---|---|---|
|[x]|T1 / 74|学习会话表与上报链路|单活跃会话互斥与终端账户绑定；后端权威，PC/H5 消费 5095/5096 提示并暂停计时|migration V20261012100000、deviceId 字段、5095/5096、OpenAPI 同步|跨终端/跨内容/同设备换账户拒绝；心跳过期放行；同 trace upsert 幂等不重复累计；单测/SQL IT/前端提示用例|
|[x]|T2 / 74|学习页计时门控|随机验证题弹窗与防挂机判定；PC/H5 双端|无新接口（复用既有 TIMEOUT）|随机间隔弹出、弹窗暂停计时、答对恢复、超时结束会话并不计时；两端定向用例/typecheck/lint|
|[x]|T3 / 75|水印工具与学习页|员工编号优先、随机化平铺水印；PC/H5 双端|无新接口（快照开关不变）|不定时不定位置重建、考试/作答页不受影响、员工编号优先；两端定向用例|

最终同步 OpenAPI、主清单、进度表、QA/Retro；OpenSpec strict、diff check 和规格/代码质量双轴 Review。Docker SQL IT 阻塞如实记录，真实验收前不整项关闭。

## 2026-10-12 T1 代码交付（学习互斥）

- 后端权威互斥：`sts_course_study_session` 增加 `device_id` 与 `(user_id, update_time)`、`(device_id, update_time)` 活跃索引；新追踪标识落库前校验同一账户其他活跃上下文（5095）与同一终端其他账户（5096）；活跃窗口 90 秒（3 个 30 秒检查点周期），`endReason=HEARTBEAT` 保持活跃、非 HEARTBEAT 收尾立即释放；同 trace 幂等路径不重复校验，重复上报不双计。
- 契约：`endReason` 增加 `HEARTBEAT`；`StudySessionReportRequest` 增加必填 `deviceId`；`course.openapi.json` 同步字段说明、枚举、示例与 5095/5096 错误码；migration `V20261012100000`、init `schema-course.sql`、init `schema-version` 与 IT 基线常量同步到 20261012100000（IT 基线不同步会让快照与增量重复执行而失败，已修）。
- 双端接通：PC/H5 学习页定时检查点改 `HEARTBEAT`，上报携带持久化终端标识（PC localStorage、移动 uni storage，shared 统一生成口径）；收到 5095/5096 停止计时与定时上报并展示中文提示；PC 课程列表页进入记录上报补 `deviceId`。
- 验证：`CourseLearningServiceImplTest` 新增 3 项（5095 拒绝、5096 拒绝、deviceId 落库）并强化同 trace 幂等不触发互斥断言；WebSlice 载荷同步 deviceId；课程域单测全通过；PC/H5 typecheck 通过；新增 node 定向 6/6；存量课程学习测试 37/41（4 项失败与 HEAD 基线一致：AssetPreview 2 项、课程作答 1 项、附件文案 1 项，均为存量，零新增）；OpenSpec strict、OpenAPI JSON、git diff --check 通过。
- 真实 SQL IT 已补（其他上下文活跃/显式收尾/心跳过期、同终端其他账户/同账户/过期），编译通过；**Docker daemon 在线但 Docker Hub 不可达（registry 超时）且本地无 mysql:8.0.45，IT 未执行**，待环境可用补跑。
- 模块全量单测 1347 项：2 失败/15 错误与 HEAD 基线 worktree 完全一致（ffmpeg/qpdf 环境缺失、ExamControllerTest 存量 1 项），零新增；误跑 IT 的 46 个错误均为 Docker 拉取阻塞，非代码问题。

## 2026-10-12 T2 代码交付（防挂机验证题）

- shared 新增 `learning-verification.ts`：60～180 秒随机调度间隔、60 秒作答时限、两位数以内三种算术题（加法两位数操作数、减法结果非负、乘法一位数操作数），纯函数双端共用。
- PC/H5 学习页：随机间隔弹出验证题（PC ElDialog、H5 自绘弹层），弹窗期间暂停有效学时累计；答对恢复计时并按新的随机间隔调度；60 秒超时未答对以 `TIMEOUT` 结束当前会话、停止计时与定时上报并提示重新进入内容项；切换内容项与页面卸载清理全部定时器。
- 验证：shared 工具真实函数用例（题目正确性、减法非负、乘法范围、间隔边界）与两端页面契约用例；node 定向 9/9；两端 typecheck；移动端目标 lint 零警告、PC 零新增警告（存量 img/input 自闭合除外）。

## 2026-10-12 T3 代码交付（随机水印）

- shared `watermark.ts` 新增 `buildRandomWatermarkTile` 与 `nextWatermarkDelayMs`：单元尺寸（200～280×150～200）、旋转角（-34°～-14°）、字号（15～19）与两行锚点全部随机，锚点按旋转角折算并夹在单元内避免裁切；文字转义与固定平铺一致；`buildWatermarkTile` 保持不变，考试与课程作答页不受影响。
- PC/H5 学习页：水印文字优先员工编号（`userCode` 回退 `userName`）；`watermarkStyle` 由 computed 改为随开关重建的 ref，按 45～90 秒随机间隔调度下一次重建，开关关闭或页面卸载时清理定时器；实现「不定时不定位置」水印。
- 验证：shared 随机水印真实函数用例（尺寸/角度区间、随机差异、XML 转义）与两端页面契约用例（随机重建、员工编号优先、作答页不受影响）；node 定向 11/11；两端 typecheck；PC 目标 lint 仅存量 img/input 自闭合警告、移动端零警告；存量课程学习测试 45/49，4 项失败与 HEAD 基线一致（AssetPreview 2 项、课程作答 1 项、附件文案 1 项），零新增。

## 人工确认记录

- 2026-10-12 用户确认批次范围：74 → 75；保留防挂机、互斥、随机水印/验证题差异；强化防作弊本期延期，未确认重启不实施。
- 2026-10-12 三项范围决策均按推荐方案确认：①74 按学习域互斥实施（单活跃学习会话 + 终端标识绑定检测），登录层不全局互斥，74 不勾选整项；②75 随机化平铺水印，不加漂浮弹幕；③验证题纯前端门控，无新后端接口。
