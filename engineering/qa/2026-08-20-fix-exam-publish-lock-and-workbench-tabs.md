# QA：考试发布后批阅锁定与工作台页签

日期：2026-08-20  
OpenSpec：`2026-08-20-fix-exam-publish-lock-and-workbench-tabs` Task 1～3

## 验收结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 已发布成绩服务端锁定 | 通过 | `ExamAttemptServiceImplTest` 新增发布后批阅拒绝用例，断言 400/7032、无批阅记录写入且不更新答案。 |
| 已发布成绩批阅页只读 | 通过 | 发布状态判定模块实际执行；批阅页展示中文提示并禁用分数、意见及保存操作。 |
| 考试/课程工作台页签归并 | 通过 | `admin-tab-state.ts` 实际执行：同资源不同阶段共用 key，不同资源隔离，连续 upsert 后仅保留一个页签且更新为最新阶段路径。 |
| 考试基础信息展示 | 通过 | 两列详情信息组；考试须知独立整行并使用 `pre-wrap` 换行；字段和既有数据源不变。 |
| 后端目标测试 | 通过 | `mvn -pl sts-service-admin -am -Dtest=ExamAttemptServiceImplTest '-Dsurefire.failIfNoSpecifiedTests=false' test`，20/20。 |
| 后端全量零 DB | 部分通过 | `mvn -pl sts-service-admin -am test` 中考试相关测试通过；6 组既有素材转码测试因本机未配置绝对路径的 ffmpeg/qpdf 或相应可执行程序失败，详见“未执行/阻塞项”。 |
| 前端定向回归 | 通过 | `node --test tests/exam-publish-lock-and-workbench-tabs.test.mjs tests/exam-workspace-navigation-and-paper-defaults.test.mjs`，9/9。 |
| 前端类型检查与 lint | 通过 | `pnpm --filter @safety-training/pc-web typecheck`；受影响生产源码 `eslint --max-warnings=0`。 |
| 双轴 Review | 通过 | 规格符合性和代码质量复审均通过；首次审查指出的静态源码测试不足已改为可执行状态测试。 |

## 未执行/阻塞项

- 未在真实非生产环境以批阅教师账号验证页面交互；服务端与前端状态测试已覆盖核心限制。
- PC Web `vite build` 本轮停在转换阶段，未输出成功摘要或产生新的构建产物，因此不作为通过证据。
- 后端全量零 DB 的环境性失败：`DocPreviewProviderTest`、`ExternalProcessRunnerTest`、`FfmpegRealProcessTest`、`FfmpegCommandFactoryTest`、`PdfLinearizerTest`、`QpdfCommandFactoryTest`；均属于素材转码工具路径/可执行程序配置，不是本 Change 的考试代码失败。
