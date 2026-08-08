# mock-upstream 用户个人中心 任务清单

> 执行流程遵循 config.yaml `rules.tasks`：默认逐个任务执行（执行当前任务 → 验证 → 展示变更摘要 → 人工 review → git commit → 确认 continue）。

## 前置说明

- 所有改动仅限 `mock-upstream/` 独立目录，OTX 主项目零改动；
- 注释/Javadoc/文档全中文；日志字符串英文 + 每行上方中文注释（沿用 mock-upstream 现有风格与仓库 `rules.logging`）；
- 单测：mock-upstream 此前不写测试，本次为状态机编排逻辑新增轻量 JUnit 单测（Mockito mock OtxClient 与 BlockchainSimulator，不依赖真实 OTX/网络）。

## Task 1：新增 Web 与模板依赖

**目标**：mock-upstream 从纯逻辑应用变为可提供页面的 Web 应用。

- [x] 1.1 `mock-upstream/pom.xml` 新增 `spring-boot-starter-web`、`spring-boot-starter-thymeleaf` 依赖
- [x] 1.2 `application.yaml` 确认端口 8004 与 Thymeleaf UTF-8 配置无冲突
- [x] 1.3 冒烟测试：`MockUpstreamApplication` 上下文启动成功、`GET /` 返回 200

## Task 2：内存状态模型与注册表

**目标**：支撑多用户、充值/提现记录、操作日志的内存数据结构。

- [x] 2.1 新增 `state/SimUser.java`（uid）
- [x] 2.2 新增 `state/DepositRecord.java`（bizNo/uid/amount/currency/txHash/status，枚举 CONFIRMING/BOOKED）
- [x] 2.3 新增 `state/WithdrawRecord.java`（bizNo/uid/amount/currency/toAddress/freezeBizNo/status，枚举 FROZEN/SETTLED/CANCEL_PENDING/CANCELLED）
- [x] 2.4 新增 `state/OperationLogEntry.java`（时间/业务前缀/动作描述/HTTP 端点/响应摘要）
- [x] 2.5 新增 `state/RegistryStore.java`：用户/充值/提现表（ConcurrentHashMap）+ 操作日志环形缓冲（200 条）+ 追加日志方法
- [x] 2.6 单测：日志环形缓冲追加/读取、注册表并发安全、重复 uid 幂等

## Task 3：BlockchainSimulator 改为按需推进

**目标**：移除自动定时推块，区块仅在「一键处理」时推进。

- [x] 3.1 移除 `@Scheduled advanceBlock()` 定时方法与 `@EnableScheduling`
- [x] 3.2 新增 `advanceTo(long targetHeight)`：推进区块并在途中触发首次达标交易的确认回调（复用 callbackTriggered 防重复）
- [x] 3.3 单测：advanceTo 后确认数正确增长、确认回调仅触发一次、未达标不触发

## Task 4：场景编排器重构（发起/推进分离）

**目标**：充值、提现从"整条故事线自动跑"拆分为"单个业务动作"。

- [x] 4.1 `DepositSimulator` 拆分：`initiateDeposit(uid, amount)` 与 `bookDeposit(record)`
- [x] 4.2 `WithdrawSimulator` 拆分：`applyWithdraw(uid, amount)` / `markCancel(bizNo)` / `settleWithdraw(record)` / `cancelWithdraw(record)`
- [x] 4.3 每个动作成功/失败均追加操作日志（含 HTTP 端点与 OTX 响应摘要）
- [x] 4.4 单测：独立 bizNo/独立交易、幂等开户、已结算不可标记取消、失败时状态不变且日志含端点

## Task 5：ProcessOrchestrator 一键处理编排

**目标**：唯一的内部触发入口，批量推进所有挂起事项。

- [x] 5.1 新增 `ProcessOrchestrator.process()`：依序处理 CONFIRMING 充值 → FROZEN 提现 → CANCEL_PENDING 提现
- [x] 5.2 返回处理统计（各类型成功/失败/跳过），单笔失败不中断整体
- [x] 5.3 单测：多笔混合挂起一次推进、单笔失败不影响其余、无挂起时零动作

## Task 6：Web 层控制器

**目标**：页面渲染与用户操作端点。

- [x] 6.1 新增 `web/HomeController`：`GET /` 渲染个人中心页
- [x] 6.2 新增 `web/UserActionController`：POST /users、POST /users/{uid}/select、POST /deposits、POST /withdrawals、POST /withdrawals/{bizNo}/cancel
- [x] 6.3 新增 `web/ProcessController`：POST /internal/process（重定向回主页并携带统计摘要）
- [x] 6.4 测试：各端点 302 重定向回主页、非法参数返回错误提示页而非 500

## Task 7：个人中心页面模板

**目标**：模拟用户个人中心 UI。

- [x] 7.1 新增 `templates/home.html`：资产卡片、充值/提现表单、一键处理按钮（待处理计数）、记录列表（状态标签/取消按钮）、操作日志区、用户切换
- [x] 7.2 样式与 admin-console 页面风格一致（浅色、卡片式、中文）

## Task 8：移除自动演绎

**目标**：启动不再自动跑故事线。

- [x] 8.1 删除 `DemoRunner` 自动执行（含 `ApplicationRunner` 实现）
- [x] 8.2 清理 `SimulatorProperties` 中仅自动演绎使用的延时配置（step-delay-ms）
- [x] 8.3 冒烟测试：应用启动后无自动 HTTP 调用（启动后 OtxClient 零调用）

## Task 9：README 与规范同步

**目标**：文档与规范跟上新交互形态。

- [x] 9.1 更新 README「一键体验」章节：操作方式改为"打开 http://localhost:8004 个人中心手动触发 + 一键处理"
- [x] 9.2 合入 delta spec：更新 `openspec/specs/mock-upstream/spec.md` 主规范
