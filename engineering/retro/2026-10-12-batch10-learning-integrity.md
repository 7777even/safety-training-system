# 第十批（74 → 75）开发 Retro

日期：2026-10-12　Change：`openspec/changes/2026-10-12-complete-batch10-learning-integrity`

## 做对了什么

- 先四件套 + 三项范围决策人工确认，再动代码；未出现"先实现后补提案"。
- 互斥模型在实现前核对了前端 `clientTraceId` 递增语义与 `endReason` 使用方式，避免了"同 trace upsert 心跳"的初始误判（实际每次上报都是新行），改用 `update_time` 服务端时钟 + HEARTBEAT/USER_EXIT 收尾语义，与既有 30 秒上报机制兼容。
- `INIT_SCHEMA_VERSION`（IT 基线）与 init `schema-version`、migration 三处同步意识在前，未留"快照吸收增量后又跑增量"的重复 DDL 隐患。
- 失败甄别坚持 HEAD 基线对照（worktree），把存量失败与新增失败分开记账，未把环境问题算成本批回归。

## 坑与教训

- **文件写入偶发不落盘**：`write_to_file` 报成功但文件不存在（PC `learning-device.ts`、shared `learning-verification.ts` 各一次），直到测试/编译报 ENOENT 才发现。教训：并行批量写文件后必须立即用目录列表或 `dir` 验证落盘，再跑后续验证。
- **测试改动误伤结构**：一次 replace 把既有测试的声明行吞进新测试，导致语法错位；改动测试文件后应先跑一次再继续叠内容。
- **模板 lint 细节**：uni 端 `<input>` 既不能自闭合（规则警告）也不能写闭合标签（编译错误），正确写法是裸 `<input ...>`；照搬 PC 写法会连踩两个坑。
- **`Math.min` 与 `toFixed` 混用**类型错误：数值计算与字符串格式化要分开，先算数再 `.toFixed(1)`。
- **`-Dtest=com.sts.course.**` 会覆盖 surefire 的 IT 排除配置**，导致 46 个 IT 因 Docker 拉取阻塞误跑报错；跑"课程域单测"应使用默认 `mvn test` 或明确排除 IT 的写法，避免把环境阻塞误判为代码失败。

## 流程改进建议

- 批量写文件后增加"落盘核验"步骤（list_dir/dir）再进入验证环节。
- 前端页面大改动（双端同款）可先在 shared 放纯函数与常量，两端只做薄封装，本次验证题与水印均如此，回归成本低。
