# 充值入账前链上确认检查（deposit-chain-confirmation）

## Why

充值入账前缺少链上确认检查是当前最严重的财务安全漏洞：`DepositAppServiceImpl.deposit()` 收到请求后立即增加用户余额、记录流水并过账，**不验证链上交易是否真实存在、是否已打包、是否达到安全确认数**。若上游业务系统监听到未确认交易、孤块交易甚至伪造的 txHash 便通知 OTX 入账，资金将被错误记入用户余额，且流水与总账均为 append-only，事后修复成本极高。

链上查询能力已就绪（`ChainQueryPort` 出站端口 + `Web3jChainQueryAdapter` 实现，`isConfirmed` 方法已可用），但充值流程尚未接入。README「充值」模块已明确规划该能力：「入账前链上确认检查——充值入账前先调用链上查询接口，确认链上交易已达到配置的安全确认数（如 12 个确认），防止未确认或孤块交易入账」，当前标记 ⏳ 未完成。本变更将该规划落地。

## What Changes

- **充值请求携带链上证据**：新增专用 `DepositRequestDTO`（继承 `ChangeAmountRequest`），携带 `chainId`、`chainTxHash` 与可选 `requiredConfirmations` 覆盖值。`ChangeAmountRequest` 为充值/提现共用 DTO，不向其追加链字段，避免污染提现语义。
- **入账前链确认闸门**：`DepositAppServiceImpl.deposit()` 在调用 `changeAmountWithFundFlow` 之前，先通过 `ChainQueryPort.isConfirmed(chainId, txHash, requiredConfirmations)` 校验链上确认数。未确认或链上查无交易 → 拒绝入账（业务错误码 `DEPOSIT_TX_NOT_CONFIRMED`），余额、流水、凭证均不产生。
- **链查询失败安全拒绝**：RPC 不可用等查询异常（`Web3jRpcException`）统一转为业务错误码 `DEPOSIT_CHAIN_QUERY_FAILED`，fail-safe 拒绝入账——金融中间件在链状态不可知时不得放行资金。
- **参数校验**：`chainId` / `chainTxHash` 缺失或非法时拒绝入账（新增业务错误码 `DEPOSIT_CHAIN_INFO_MISS`）。
- **凭证链字段落库**：`buildDepositJournalRequest` 不再将 `chainId` / `chainTxHash` / `blockNumber` 置 null，而是从请求与链上回执填充，支撑后续「链上与链下对账」能力（README 对账模块规划）。
- **新增配置**：`web3j.required-confirmations`（默认 12），请求级 `requiredConfirmations` 可覆盖。
- **错误码新增**：`DEPOSIT_CHAIN_INFO_MISS` / `DEPOSIT_TX_NOT_CONFIRMED` / `DEPOSIT_CHAIN_QUERY_FAILED` 三个业务错误码（单一事实源 `BizErrorEnum`，遵循现有 `LEDGER_*` / 前缀命名惯例）。

## 设计方案

- **领域层零改动**：确认检查是应用层编排职责，`ChainQueryPort` 契约已具备所需方法，不新增领域端口、不修改聚合根。
- **应用层编排**：`deposit()` 流程变为「参数校验 → 链确认闸门 → 余额变更 + 流水（REQUIRES_NEW，幂等）→ 凭证过账（失败不回滚，最终一致性）」。
- **事务边界**：链确认为只读 RPC，在数据库事务之外执行；原有 REQUIRES_NEW 余额变更与流水事务、过账容错语义保持不变。
- **幂等不变**：`bizNo` 唯一索引仍为幂等兜底；链确认闸门每次请求都会执行（重复请求代价为一次 RPC，可接受，不做幂等预判短路）。

## 非目标

- **不含广播（broadcast）**：本变更只做入账前的确认检查，不包含向链上广播交易的能力（归属 `tx-broadcast-orchestration` 变更）。
- **不含提现侧链确认**：提现发链后的确认检查归属 `tx-broadcast-orchestration` 变更，本变更只覆盖充值入账方向。
- **不含事件监听**：上游业务系统仍负责监听链上事件并调用 OTX 充值接口，本变更不引入事件监听器。
- **不含多链配置化**：`Web3jProperties` 当前为单链架构，本变更沿用单链 + 默认确认数 + 请求级覆盖，不做 per-chain 确认数配置表。
- **不含金额/收款地址核对**：链上回执金额与请求金额的一致性校验属对账模块范畴，本变更不做。

## Capabilities

### New Capabilities

- `deposit-chain-confirmation`：充值入账前的链上确认检查能力——入账请求必须携带链上证据，系统在入账前验证链上交易已达安全确认数，未确认或查询失败时拒绝入账并返回明确业务错误码，确认通过后将链字段写入总账凭证。

### Modified Capabilities

无。现有 specs（account / ledger / fundflow / business-ledger-integration / testing）的需求均不因本变更改变；`ChangeAmountRequest` 本身不被修改（新增 DTO 继承它）。

## Impact

- **otx-common**：`BizErrorEnum` 新增 3 个错误码。
- **otx-application**：新增 `DepositRequestDTO`；`DepositAppServiceImpl` 注入 `ChainQueryPort` 并增加确认闸门编排；`buildDepositJournalRequest` 填充链字段；`DepositAppService` 接口签名由 `ChangeAmountRequest` 调整为 `DepositRequestDTO`。
- **otx-interface**：`DepositController.deposit()` 请求体由 `ChangeAmountRequest` 调整为 `DepositRequestDTO`（REST 契约向后兼容——新增字段为可选/必填校验由应用层保证）。
- **otx-infrastructure**：`Web3jProperties` 新增 `requiredConfirmations` 配置属性。
- **otx-starter**：`application-dev.yaml` 新增 `web3j.required-confirmations` 配置项。
- **依赖**：application 模块新增对 `ChainQueryPort`（domain 出站端口）的使用，无新增第三方依赖。
- **数据库**：无 schema 变更（凭证链字段列已存在，当前实现置 null，本变更开始填充）。
