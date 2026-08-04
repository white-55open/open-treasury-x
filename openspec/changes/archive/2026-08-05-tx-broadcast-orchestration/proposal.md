# tx-broadcast-orchestration 变更提案

## Why

当前 OTX 的链上能力仅有只读查询（`ChainQueryPort`），提现交易**从不向链上广播**；`withdraw-two-phase` 变更（本变更前置，已完成）已把提现拆为冻结/结算/解冻三用例，但结算（`withdraw()`）仍只做链下账务——扣冻结余额、记录流水、过账总账凭证，不触碰链上。与此同时，总账凭证（`LedgerJournalEntity`）已预埋 `chainId / chainTxHash / blockNumber / tokenAddress / confirmations` 字段和 DRAFT → POSTED → REVERSED 状态机，但状态机从未被激活——所有凭证创建即过账，DRAFT 状态形同虚设。

本变更按已确认的架构方向（Form B）补上缺失的编排层：OTX 作为被动中间件**编排**提现交易的签名与广播，签名能力可插拔、私钥**绝不进入 OTX 核心**，广播后凭证保持 DRAFT、链上确认后再结算过账。这是提现从"账务模拟"走向"真实链上出金"的关键一步，同时为未来的资金归集（归集）复用同一套广播端口。

## What Changes

- **新增出站端口（domain 层）**：`SignerPort`（对原始交易签名，返回签名字节/十六进制）与 `TxBroadcastPort`（广播已签名交易返回 `txHash`，并提供获取当前 nonce 的辅助方法）。签名实现可插拔——本地 keystore 仅限开发环境，生产对接 KMS/MPC/Fireblocks/Cobo（本期**不实现**生产签名适配器）。
- **新增提现请求聚合根**：`WithdrawRequestEntity`（`withdraw` 限界上下文）+ `WithdrawRequestRepo`，持久化提现的链上编排状态（目标地址、txHash、状态机 PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED），新增 `withdraw_request_t` 表（Flyway V5 迁移）。
- **新增链上广播用例**（`WithdrawAppService.broadcast()`，**修订说明**：原提案的"改造 `withdraw()`"在 withdraw-two-phase 变更后不可行——`withdraw()` 已承载结算语义且已实现，广播编排另立 `broadcast()` 方法）：在冻结用例（前置变更）之上构造交易 → SignerPort 签名 → TxBroadcastPort 广播 → 记录 txHash → 凭证保持 DRAFT（资金仍在冻结中）；新增 `confirmAndSettle(bizNo)` 结算用例（链上确认后复用前置变更的结算能力扣冻结余额+流水+凭证过账）与取消/状态查询用例。
- **激活总账 DRAFT 状态机**（ledger 上下文）：新增 `LedgerAppService.createDraftJournal()`（幂等创建 DRAFT 凭证，含链上字段）与 `postJournalByBizNo()`（按 bizNo 将 DRAFT 过账为 POSTED），凭证状态转换仍只走聚合根 `post()` 领域方法。
- **配置**：新增 `otx.chain-tx.*` 配置段（必填确认数、广播适配器选择、签名适配器选择、可选轮询参数）。
- **错误码**：`BizErrorEnum` 新增链上交易类错误码（`TX_BROADCAST_FAILED`、`TX_SIGN_FAILED`、`TX_NOT_CONFIRMED_YET`、`TX_CHAIN_FAILED` 等，清单见 design.md）。
- **结算确认策略**：以显式 `confirmAndSettle(bizNo)` API 为主契约（由上游回调/对账任务/调度任务调用），保持 OTX 被动中间件定位；可选内置 `@Scheduled` 轮询作为扩展，默认关闭。
- 基础设施：新增 `Web3jTxBroadcastAdapter`（TxBroadcastPort 实现）与开发专用 `LocalKeystoreSigner`（SignerPort 实现，代码与文档显著标注 dev-only）。

## Capabilities

### New Capabilities

- `tx-broadcast`: 链上交易广播与确认编排能力——提现交易构造、可插拔签名、广播、确认结算、失败解冻全链路。包含签名与广播出站端口契约、提现请求状态机、结算幂等语义。

### Modified Capabilities

- `ledger`: 激活 Journal DRAFT 状态机——新增 DRAFT 凭证幂等创建与按 bizNo 过账两个用例，链上字段（chainId/chainTxHash）在广播场景下被实际填充与查询。相应 delta 规格见 `specs/ledger/spec.md`。

## Impact

- **依赖关系**：本变更**依赖 `withdraw-two-phase` 变更**（提供冻结/解冻能力 `AccountEntity.freezeBalance/unfreezeBalance` + 应用层用例），结算与取消路径建立在其上；`withdraw-two-phase` 与本变更为顺序实施关系，冻结先行。
- **领域层**：`io.github.open55.otx.domain.chain.port` 新增 `SignerPort`/`TxBroadcastPort`/值对象；`io.github.open55.otx.domain.withdraw` 新增 `WithdrawRequestEntity`/`WithdrawRequestRepo`（withdraw 首次在 domain 层拥有领域实体）。
- **应用层**：`WithdrawAppServiceImpl` 重构（编排端口与仓储）；`LedgerAppServiceImpl` 新增两个方法；`WithdrawAppService` 接口扩展。
- **基础设施层**：`blockchain` 包新增广播适配器与本地签名适配器；`repository` 新增 `WithdrawRequest` 四件套（PO/Mapper/Converter/RepoImpl）；`docs/sql/` 新增 V5 迁移。
- **接口层**：`WithdrawController` 新增确认结算、取消、状态查询端点；提现请求体新增链上字段（目标地址、token 地址等）。
- **配置**：`otx-starter` 的 application*.yaml 新增 `otx.chain-tx.*` 段。
- **非目标（Non-Goals）**：OTX 核心**不存储/管理私钥**（私钥仅存在于外部签名设施）；**不实现**事件监听/链上监控、资金归集（归集）、生产级 KMS/MPC 签名适配器；**不改动**充值（deposit）流程；不修改现有 `ChainQueryPort` 及其 web3j 实现。
