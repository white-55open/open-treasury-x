# 模拟上游（mock-upstream）设计文档

## Context（背景与现状）

OTX 全部能力以 REST API 暴露，其业务故事需要外部调用方来"演绎"。当前验证手段只有 curl 手工调用与集成测试（后者无业务叙事）。`admin-console` 变更提供"看"的界面，本变更提供"演"的驱动——二者配合构成完整的业务显性化闭环。

关键约束（架构真实性）：模拟上游必须是**外部系统**视角——只通过 HTTP 调 OTX REST API（`http://localhost:8003`），不 import 任何 OTX Maven 模块、不共享任何类。这样模拟的就是真实集成场景，未来替换为真实上游时零概念差异。

## Goals / Non-Goals（目标 / 非目标）

**目标：**
- 独立模块 `mock-upstream/`（仓库根，独立 pom，不加入 reactor）；
- 链上事件模拟（区块增长、交易确认数演化、确认达标触发充值入账）；
- 提现故事线自动编排（freeze → broadcast → confirm-settle）+ 取消故事线（freeze → cancel）；
- 控制台中文业务日志，配合 admin-console 实时核对落账。

**非目标：**
- 不连真实链、不写单测、不做 UI、不做并发压力（见 proposal Non-Goals）。

## Decisions（关键决策）

### D1：独立目录 + 独立 pom（不进 reactor）

- `mock-upstream/pom.xml` 为独立 Spring Boot 应用（parent 独立或直接依赖 spring-boot-starter-parent），不加入 `open-treasury-x/pom.xml` 的 `<modules>`；
- 理由：① 它是"外部系统"，物理隔离强化心智模型；② 避免主构建（`mvnw clean install`）依赖模拟器编译；③ 可独立 `mvn spring-boot:run` 启动。
- 端口 `server.port=8004`，OTX 地址 `otx.base-url=http://localhost:8003`（application.yaml + 环境变量覆盖）。

### D2：链上事件模拟 = 内存状态机

- `BlockchainSimulator`（`@Component`）：内存维护 `currentBlockNumber`（定时 +1，间隔可配）+ 交易表 `Map<txHash, SimulatedTx>`；
- `SimulatedTx`：from/to/amountWei/txHash/createdBlock/status("0x1")；
- 确认数 = `currentBlockNumber - createdBlock + 1`；
- 充值流程：`DepositSimulator` 生成交易 → 注册到模拟链 → 每区块推进时检查确认数，达阈值（`otx.required-confirmations`，默认 12）→ 调 `POST /deposit`（body：uid/amount/bizNo/currency/chainId/chainTxHash）。
- 定时驱动：`@Scheduled(fixedDelay = ...)` 推进区块（demo 模式默认每 2 秒一块，可配——演示"确认数增长"过程）。

### D3：提现故事线 = 顺序编排器

- `WithdrawSimulator`（`@Component`）提供 `runSettlementStory()` 与 `runCancellationStory()`：
  - 结算线：`POST /withdraw/freeze`（body：uid/bizNo/amount/currency）→ `POST /withdraw/broadcast`（+chainId/toAddress）→ 模拟确认数达标 → `POST /withdraw/{bizNo}/confirm-settle`；
  - 取消线：freeze 后直接 `POST /withdraw/{bizNo}/cancel`；
- 每步日志格式：
  ```
  [业务故事] 步骤 3/5：提现广播
  POST http://localhost:8003/withdraw/broadcast
  → 200 {"code":"200","data":{"bizNo":"WD-...","txHash":"0x...","status":"BROADCASTED"}}
  ```
- 真实 REST 调用用 Spring `RestClient`；`Result<T>` 响应解析展示 code/data/message。

### D4：演示模式 = 启动即自动演绎

- `DemoRunner`（`ApplicationRunner`）：启动后按顺序执行——充值故事 → 结算故事 → 取消故事；
- 每步之间延时（可配，默认 3 秒），便于配合 admin-console 观察；
- 日志用 `System.out`/log4j2 中文输出（模拟器是工具，不强制仓库日志铁律，但保持中文可读）。

### D5：与 admin-console 的配合关系

- mock-upstream 只负责"调用"，admin-console 只负责"展示"；两者通过 OTX REST API 间接联动，无直接依赖；
- 推荐体验流程（README 记录）：启动 MySQL/Redis → 启动 OTX（8003）→ 启动 mock-upstream（8004，自动演绎）→ 打开 `http://localhost:8003/admin` 观察落账。

## 模块结构（ASCII）

```
mock-upstream/
├── pom.xml                     （独立 Spring Boot 应用）
└── src/main/
    ├── java/.../mockupstream/
    │   ├── MockUpstreamApplication.java    （@SpringBootApplication @EnableScheduling）
    │   ├── config/SimulatorProperties.java （@ConfigurationProperties("otx")：base-url/required-confirmations/block-interval-ms/step-delay-ms）
    │   ├── blockchain/BlockchainSimulator.java  （区块推进 + 交易确认演化）
    │   ├── blockchain/SimulatedTx.java           （内存交易模型）
    │   ├── upstream/OtxClient.java               （RestClient 封装 OTX API 调用）
    │   ├── scenario/DepositSimulator.java        （充值故事）
    │   ├── scenario/WithdrawSimulator.java       （提现结算/取消故事）
    │   └── scenario/DemoRunner.java              （ApplicationRunner 顺序演绎）
    └── resources/application.yaml                （8004 端口 + otx.base-url 等）
```

## 与 OTX 的接口契约（模拟器调用，HTTP）

| OTX 端点 | 调用方场景 | 模拟器入参 |
|----------|-----------|------------|
| `POST /deposit` | 充值确认达标入账 | uid/amount/bizNo/currency/chainId/chainTxHash |
| `POST /withdraw/freeze` | 提现申请冻结 | uid/bizNo/amount/currency |
| `POST /withdraw/broadcast` | 提现广播 | uid/bizNo/amount/currency/chainId/toAddress |
| `POST /withdraw/{bizNo}/confirm-settle` | 链上确认结算 | bizNo（path） |
| `POST /withdraw/{bizNo}/cancel` | 取消解冻 | bizNo（path） |
| `GET /accounts/{uid}` | 每步后核对余额 | uid（path） |

## 数据库 Schema 变更

**无**（模拟器零持久化，纯内存 + HTTP）。

## 错误码 / 配置

- 无新增错误码；
- 配置：`otx.base-url`（默认 http://localhost:8003）、`otx.required-confirmations`（默认 12）、`otx.block-interval-ms`（默认 2000）、`otx.step-delay-ms`（默认 3000）、`server.port`（8004）。

## 测试策略

- 本变更**不写单测**（运行工具，proposal Non-Goals）；正确性验证方式：
  1. 启动 OTX（dev profile，MySQL/Redis 就绪）→ 启动 mock-upstream → 观察日志全流程无异常；
  2. 配合 admin-console：控制台账户总览/提现单据/流水时间线出现对应数据（充值入账、提现状态机推进）；
  3. 重复执行幂等验证：同 bizNo 重复演绎不产生重复入账（OTX 幂等兜底）。
- 验证命令：`cd mock-upstream && mvn spring-boot:run`（或 `java -jar`）。

## Risks / Trade-offs（风险与取舍）

- **[广播/结算在 dev 环境的真实性]** → OTX 侧 dev profile 下广播依赖 LocalKeystoreSigner（需 keystore 文件）与 Web3j RPC（可配置为不可用）；若广播失败，模拟器日志会展示 FAILED 状态与错误码——这本身也是"真实集成行为"的展示，不阻塞故事线（取消线仍可演示）。README 说明 dev 环境需准备 keystore 或接受广播失败路径展示。
- **[模拟器无单测]** → 定位为演示工具；其与 OTX 的契约正确性由集成运行验证，若后续升级为测试框架再补测试。
- **[独立 pom 不参与主构建]** → 好处是主构建零影响；代价是 mock-upstream 无法复用 OTX 的依赖管理（其依赖仅 spring-boot-starter + RestClient，影响极小）。
