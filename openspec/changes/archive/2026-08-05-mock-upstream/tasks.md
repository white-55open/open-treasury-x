# 模拟上游（mock-upstream）实施任务清单

按依赖顺序执行：项目骨架 → 链上模拟 → OTX 客户端 → 故事线 → 演示驱动 → 端到端验证。本变更为独立目录运行工具（不加入 Maven reactor），**不写单测**（proposal Non-Goals），正确性由端到端运行验证。

## 1. 项目骨架

- [x] 1.1 创建 `mock-upstream/pom.xml`：独立 Spring Boot 应用（spring-boot-starter-parent + spring-boot-starter-web 或最小 web 依赖 + spring-boot-starter 调度），不加入仓库根 pom 的 modules；`<java.version>21</java.version>`
- [x] 1.2 创建 `MockUpstreamApplication.java`（@SpringBootApplication + @EnableScheduling，中文类 Javadoc 说明定位：OTX 外部调用方模拟器，仅 HTTP 交互）
- [x] 1.3 创建 `config/SimulatorProperties.java`（@ConfigurationProperties(prefix="otx")）：baseUrl（默认 http://localhost:8003）/ requiredConfirmations（默认 12）/ blockIntervalMs（默认 2000）/ stepDelayMs（默认 3000）；每字段中文 Javadoc；在启动类或配置类注册
- [x] 1.4 创建 `resources/application.yaml`：server.port=8004 + otx 配置段（含中文注释）
- 验收：`cd mock-upstream && mvnw.cmd compile` 通过（独立构建）

## 2. 链上事件模拟器

- [x] 2.1 `blockchain/SimulatedTx.java`：内存交易模型（txHash/from/to/amountWei/createdBlock/status），字段 final + 中文 Javadoc
- [x] 2.2 `blockchain/BlockchainSimulator.java`（@Component）：内存区块高度 + 交易注册表；`advanceBlock()` 区块 +1；`registerTx(...)` 生成模拟交易（txHash 用随机 hex，createdBlock=当前高度）；`confirmationsOf(txHash)` 计算确认数；`@Scheduled(fixedDelayString="${otx.block-interval-ms:2000}")` 定时推进区块；中文 Javadoc 与日志（日志含当前区块高度与各交易确认数）
- 验收：`mvn spring-boot:run` 启动后日志显示区块推进

## 3. OTX 客户端

- [x] 3.1 `upstream/OtxClient.java`（@Component）：基于 Spring RestClient 封装以下调用（每个方法中文 Javadoc + 返回响应字符串或简单 DTO）：
  - `deposit(Map<String,Object> body)` → POST {baseUrl}/deposit
  - `freeze(Map<String,Object> body)` → POST /withdraw/freeze
  - `broadcast(Map<String,Object> body)` → POST /withdraw/broadcast
  - `confirmSettle(String bizNo)` → POST /withdraw/{bizNo}/confirm-settle
  - `cancel(String bizNo)` → POST /withdraw/{bizNo}/cancel
  - `getAccount(Long uid)` → GET /accounts/{uid}
  - 所有调用打印：端点 + 响应体（Result JSON）
- 验收：OTX 启动后手工调用一个方法验证连通

## 4. 故事线编排

- [x] 4.1 `scenario/DepositSimulator.java`（@Component）：`runDepositStory()`——生成 uid/bizNo/金额 → BlockchainSimulator.registerTx → 轮询确认数（或依赖定时推进回调）达到 requiredConfirmations → OtxClient.deposit → 打印结果；中文日志（步骤说明 + 确认数演化）
- [x] 4.2 `scenario/WithdrawSimulator.java`（@Component）：`runSettlementStory()`（freeze → 轮询/等待广播 → broadcast → 等待确认 → confirmSettle，每步 3 秒延时可配）+ `runCancellationStory()`（freeze → cancel）；每步打印"步骤 n/N：中文说明 + 端点 + 响应"
- [x] 4.3 故事线内每步后调用 getAccount(uid) 打印余额快照（可用/冻结），展示 OTX 落账联动
- 验收：OTX 运行中手工触发两个故事线，日志完整、OTX 侧数据正确

## 5. 演示驱动

- [x] 5.1 `scenario/DemoRunner.java`（ApplicationRunner）：启动后顺序执行 充值故事 → 结算故事 → 取消故事，步骤间 stepDelayMs 延时；结束时输出总结（各步骤成功/失败统计 + 提示打开 admin-console 查看）
- 验收：完整端到端运行

## 6. 端到端验证与文档

- [x] 6.1 端到端验证（本地 MySQL:3307/Redis:6380 + OTX dev 启动 + mock-upstream 启动）：① 日志全流程无异常；② 重复演绎同 bizNo 幂等（不重复入账）；③ 配合 admin-console 页面核对数据（若控制台已实现）
- [x] 6.2 README 增加「一键体验」章节（可选，若 admin-console 已完成则说明 启动顺序：MySQL/Redis → OTX → mock-upstream → 打开 /admin；若广播失败路径，说明 dev keystore 准备要求）
- 验收：README 体验流程按步骤可复现

