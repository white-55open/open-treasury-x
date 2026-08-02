# 充值入账前链上确认检查（deposit-chain-confirmation）设计

## Context

- **现状**：`DepositAppServiceImpl.deposit(ChangeAmountRequest)` 收到请求后直接调用 `accountAppService.changeAmountWithFundFlow(request)` 增加余额并落流水，随后 `buildDepositJournalRequest` 构造凭证（`chainId` / `chainTxHash` / `blockNumber` / `tokenAddress` 全部硬编码为 null）过账。**全程无链上确认**。
- **已有能力**：domain 出站端口 `ChainQueryPort` 已定义 `queryTxReceipt` / `currentBlockNumber` / `isConfirmed(chainId, txHash, requiredConfirmations)`，infrastructure 的 `Web3jChainQueryAdapter` 已实现（多 RPC 故障切换，全部失败抛 `Web3jRpcException`，链 ID 未配置抛 `LEDGER_CHAIN_NOT_CONFIGURED`），配置在 `web3j.chain-id` / `web3j.rpc-urls` / `web3j.read-timeout`。**只是充值流程未接入**。
- **约束**：金融中间件的入账动作（余额增加 + 流水 + 凭证）必须建立在可验证的链上事实之上；流水与凭证 append-only，入账后无法回滚（只能冲销，成本高）。
- **相关方**：上游业务系统（监听链上事件后调用 OTX 充值接口）、OTX 充值/账户/总账/链上查询四个限界上下文。
- **已有惯例**：幂等 = bizNo + `fund_flow_t` 唯一索引；动账 = `changeAmountWithFundFlow`（REQUIRES_NEW + 乐观锁 + `@Retryable`）；过账失败不回滚（最终一致性）；DTO 命名统一 `*DTO` 后缀（旧类 `ChangeAmountRequest` 为历史遗留，不模仿）。

## Goals / Non-Goals

**Goals:**

- 充值入账前强制链上确认检查，未确认/孤块/伪造交易不得入账。
- 链查询失败时 fail-safe 拒绝入账，不得在链状态未知时放行资金。
- 凭证携带链上证据（chainId / chainTxHash / blockNumber），支撑后续链上与链下对账。
- 领域层零改动，纯应用层编排 + 配置扩展。

**Non-Goals:**

- 不做广播（broadcast）、不做提现侧确认、不做事件监听（见 proposal 非目标）。
- 不做链上金额/收款地址与请求金额的一致性核对（对账模块范畴）。
- 不做 per-chain 确认数配置表（`Web3jProperties` 当前单链架构）。
- 不修改 `ChangeAmountRequest`（充值/提现共用，避免污染提现语义）。

## Decisions

### D1：新增专用 `DepositRequestDTO`，而非扩展 `ChangeAmountRequest`

`ChangeAmountRequest` 是充值/提现共用的资金变更请求（`DepositController` 与 `WithdrawController` 均直接接收）。若在其中追加 `chainId` / `chainTxHash`，则提现请求也被迫携带（或容忍空值），污染提现语义；且提现侧链确认归属 `tx-broadcast-orchestration` 变更，其链字段需求可能不同。

**决策**：新建 `DepositRequestDTO extends ChangeAmountRequest`（包 `io.github.open55.otx.application.deposit.dto`），新增三个字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chainId` | String | 是 | 区块链 ID，如 "11155111" |
| `chainTxHash` | String | 是 | 链上交易哈希 |
| `requiredConfirmations` | Integer | 否 | 所需确认数，缺省取配置默认值；必须为正整数 |
| `tokenAddress` | String | 否 | 代币合约地址（预留，当前可空） |

`DepositAppService.deposit` 签名与 `DepositController` 请求体同步调整为 `DepositRequestDTO`。REST 契约新增字段为可选接收，旧调用方不带链字段时会被应用层以 `DEPOSIT_CHAIN_INFO_MISS` 拒绝——**这是有意的安全收紧**（充值入账从此必须携带链证据）。

**替代方案**：直接给 `ChangeAmountRequest` 加链字段 → 被否，理由如上（污染提现语义）。

### D2：确认数来源 = 配置默认值 + 请求级覆盖

新增 `web3j.required-confirmations` 配置属性（默认 `12`），挂到 `Web3jProperties`（`@ConfigurationProperties(prefix = "web3j")`）。应用层解析逻辑：

```
requiredConfirmations = (request.requiredConfirmations != null && request.requiredConfirmations > 0)
                        ? request.requiredConfirmations
                        : properties.getRequiredConfirmations();
```

**替代方案**：per-chain 配置表（`Map<String, Integer>`）→ 暂不做，当前单链架构（`Web3jProperties.chainId` 单一值），引入 Map 属过度设计；未来多链时再演进。

### D3：链查询失败策略 = fail-safe 拒绝入账

`Web3jChainQueryAdapter` 在全部 RPC 节点不可用时抛 `Web3jRpcException`。应用层捕获该异常并转换为 `BizException(DEPOSIT_CHAIN_QUERY_FAILED)` 拒绝入账。

**理由**：链状态未知时放行资金 = 可能把孤块/未确认交易记入余额；拒绝入账只造成一次业务失败（上游可稍后重试），不造成资金损失。金融中间件必须 fail-safe。

**替代方案**：fail-open（查询失败放行）→ 被否，财务风险不可接受；fail-open 仅适用于非资金敏感场景。

### D4：确认闸门位于动账事务之外、`changeAmountWithFundFlow` 之前

链确认检查是只读 RPC 调用（无 DB 写），不放入任何事务；若确认失败，直接抛业务异常，`changeAmountWithFundFlow`（REQUIRES_NEW）根本不会启动，余额/流水/凭证零副作用。原有事务边界、乐观锁重试、过账容错语义全部保持不变。

```
deposit(DepositRequestDTO)
  ├─ 1. 参数校验（chainId / chainTxHash 非空，requiredConfirmations 为正）→ 失败抛 DEPOSIT_CHAIN_INFO_MISS
  ├─ 2. 链确认闸门（无事务）：
  │     try: isConfirmed(chainId, chainTxHash, confirmations)
  │       false → 抛 DEPOSIT_TX_NOT_CONFIRMED
  │       true  → 查询回执（queryTxReceipt）取 blockNumber，供凭证填充
  │     catch Web3jRpcException → 抛 DEPOSIT_CHAIN_QUERY_FAILED
  ├─ 3. changeAmountWithFundFlow(request)      ← REQUIRES_NEW + 幂等 + 乐观锁重试（不变）
  └─ 4. postJournal(buildDepositJournalRequest) ← 失败仅 WARN，不回滚（最终一致性，不变）
```

**凭证链字段填充**：`chainId` / `chainTxHash` 直接取自请求；`blockNumber` 取自确认通过后 `queryTxReceipt` 返回的回执（`ChainTxReceipt.blockNumber`）；`tokenAddress` 取自请求可选字段（无则 null）。`isConfirmed` 内部已查过一次回执但端口不返回回执对象，为不扩展端口契约、保持领域层零改动，应用层在确认通过后最多再发一次 `queryTxReceipt`——一次额外 RPC 换取接口稳定性，可接受（见 R3）。

**替代方案**：扩展 `ChainQueryPort` 新增"查询并返回回执+确认数"方法 → 被否，领域端口契约变更影响面大（接口 + 适配器 + 测试），而本变更的核心诉求只是"入账前把关"。

### D5：幂等语义不变，确认闸门每次执行

`bizNo` + 唯一索引的幂等机制不动。重复请求时确认闸门仍会执行（代价一次 RPC）再进入 `changeAmountWithFundFlow` 的幂等返回路径。**不**做"先查流水存在即跳过链检查"的短路——短路会引入读取一致性窗口（同一 bizNo 并发首请求），且幂等结果语义要求"返回原始成功结果"，链检查与幂等解耦更清晰。

### D6：错误码（单一事实源 `BizErrorEnum`，沿用 `DEPOSIT_*` 前缀惯例）

新增 3 个错误码（已核对 `BizErrorEnum.java` 现有清单，`LEDGER_CHAIN_NOT_CONFIGURED` 已存在且复用，无需新增）：

| 错误码 | 消息（英文，与现有风格一致） | 触发场景 |
|--------|-----------------------------|----------|
| `DEPOSIT_CHAIN_INFO_MISS` | "Deposit request must carry chainId and chainTxHash." | chainId/chainTxHash 为空或 requiredConfirmations 非正 |
| `DEPOSIT_TX_NOT_CONFIRMED` | "Deposit transaction is not confirmed on chain." | isConfirmed 返回 false（含链上查无交易） |
| `DEPOSIT_CHAIN_QUERY_FAILED` | "Chain query failed, deposit rejected." | Web3jRpcException（RPC 全挂） |

### D7：领域模型与聚合边界无变更

确认检查是应用层编排逻辑：不新增 domain 端口、不改 `ChainQueryPort`、不改 `LedgerJournalEntity` / `AccountEntity` / `FundFlowEntity`（凭证链字段列已存在且可空）。无新增聚合、无领域事件。

## 架构影响（六边形各层）

```
otx-common      BizErrorEnum        +DEPOSIT_CHAIN_INFO_MISS / DEPOSIT_TX_NOT_CONFIRMED / DEPOSIT_CHAIN_QUERY_FAILED
otx-domain      （零改动）
otx-application DepositRequestDTO（新增）
                DepositAppService.deposit 签名 ChangeAmountRequest → DepositRequestDTO
                DepositAppServiceImpl 注入 ChainQueryPort，增加确认闸门编排 + 凭证链字段填充
otx-interface   DepositController 请求体 ChangeAmountRequest → DepositRequestDTO（REST 契约向后兼容）
otx-infrastructure Web3jProperties +requiredConfirmations（默认 12）
otx-starter     application-dev.yaml +web3j.required-confirmations: 12
```

## API 变更

**REST（POST /deposit）**：请求体由 `ChangeAmountRequest` 调整为 `DepositRequestDTO`。

```jsonc
// 变更前
{ "uid": 1001, "amount": 100, "bizNo": "DEP-001", "currency": "USDT" }
// 变更后（新增链字段）
{ "uid": 1001, "amount": 100, "bizNo": "DEP-001", "currency": "USDT",
  "chainId": "11155111", "chainTxHash": "0xabc...", "requiredConfirmations": 12 }
```

**应用层接口**：

```java
public interface DepositAppService {
    String deposit(DepositRequestDTO request);   // 签名变更
}
```

## 配置变更

```yaml
web3j:
  chain-id: "11155111"
  rpc-urls:
    - https://eth-sepolia.g.alchemy.com/v2/${ALCHEMY_KEY}
  read-timeout: 5000
  required-confirmations: 12   # 新增：充值入账所需安全确认数
```

## 数据库变更

无 schema 变更。`ledger_journal_t` 的链字段列（chain_id / chain_tx_hash / block_number / token_address）已存在，本变更从"写入 null"变为"写入真实值"。

## 事务边界与一致性

| 步骤 | 事务 | 失败处理 |
|------|------|----------|
| 参数校验 | 无 | 抛业务异常，零副作用 |
| 链确认检查 + 回执查询 | 无（只读 RPC） | 未确认/查询失败 → 抛业务异常，零副作用 |
| 余额变更 + 流水 | REQUIRES_NEW（既有） | 乐观锁冲突 → `@Retryable` 重试 5 次 |
| 凭证过账 | 既有 postJournal | 失败仅 WARN，不回滚（最终一致性，对账修复） |

跨聚合一致性策略与现状一致：余额/流水强一致（同一事务），凭证最终一致。确认闸门在两者之前，天然保证"未确认交易不可能产生任何账务副作用"。

## 测试策略

全部单元测试，**mock `ChainQueryPort`，禁止真实网络/DB**（遵守 testing 规范第 9 条禁止清单）。

- **`DepositAppServiceImplTest`（新增/扩展）**：`@Nested` 分组——「链确认闸门」/「入账编排」/「凭证链字段」/「幂等」。
  - `deposit_withConfirmedTx_creditsBalanceAndPostsJournal`：mock `isConfirmed=true` → 断言余额增加、流水落库、凭证过账被调用
  - `deposit_withUnconfirmedTx_throwsTxNotConfirmed`：mock `isConfirmed=false` → 断言 `DEPOSIT_TX_NOT_CONFIRMED`，且 `changeAmountWithFundFlow` / `postJournal` **从未被调用**（verify never）
  - `deposit_whenChainQueryFails_throwsChainQueryFailed`：mock 抛 `Web3jRpcException` → 断言 `DEPOSIT_CHAIN_QUERY_FAILED`
  - `deposit_withMissingChainId_throwsChainInfoMiss` / `deposit_withMissingTxHash_throwsChainInfoMiss`
  - `deposit_withInvalidRequiredConfirmations_throwsChainInfoMiss`：0 / 负数 / null
  - `deposit_withoutOverrideUsesDefaultConfirmations`：断言 `isConfirmed` 收到默认 12
  - `deposit_withOverrideUsesRequestConfirmations`：断言收到请求值 6
  - `deposit_withConfirmedTx_journalCarriesChainEvidence`：捕获 `PostJournalRequestDTO`，断言 chainId / chainTxHash / blockNumber 与请求及 mock 回执一致
  - `deposit_withDuplicateBizNo_returnsOriginalBizNo`：幂等路径
  - 测试数据用 `private static final` 具名常量（如 `TEST_CHAIN_ID`、`TEST_TX_HASH`、`AMOUNT_100`、`REQ_CONFIRMATIONS_12`），方法名 snake_case，`@DisplayName` 中英双语。
- **`Web3jPropertiesTest`（扩展）**：默认 `requiredConfirmations = 12`。
- **`DepositControllerTest`（更新）**：请求体换 `DepositRequestDTO`，仍断言 `POST /deposit` 返回 `Result<String>` 的 bizNo。

## Risks / Trade-offs

- **[R1] 链查询延迟**（每次充值入账增加 2 次 RPC 调用：isConfirmed + queryTxReceipt）→ 影响面：入账接口 P99 上升。缓解：web3j read-timeout 已配置（默认 5s）；对账类只读链查询可后续走缓存/异步，不在本变更范围。链查询在事务外执行，不占用 DB 连接与事务时长。
- **[R2] 上游未升级导致充值被拒**（旧调用方不带链字段 → `DEPOSIT_CHAIN_INFO_MISS`）→ 缓解：这是有意的安全收紧；README 规划早已声明"入账前链上确认检查"，上游需同步升级；拒绝是显式业务错误，可观测、可排查。
- **[R3] 确认通过后回执查询的二次 RPC 可能失败**（isConfirmed 成功但随后的 queryTxReceipt 抛 Web3jRpcException）→ 缓解：catch 同一 `Web3jRpcException` 转 `DEPOSIT_CHAIN_QUERY_FAILED` 拒绝入账（fail-safe 一致性）；回执缺失时 blockNumber 填 null 的兜底仅在"确认已通过"前提下仍允许入账（保守兜底，凭证 blockNumber 可空，对账可后补）。
- **[R4] 配置默认值变更影响存量**（`required-confirmations` 默认 12，若某链确认产出慢，充值将大面积被拒）→ 缓解：请求级 `requiredConfirmations` 可单笔覆盖；配置在 starter 层，发布前可评估调整。
- **[R5] 链 ID 不匹配**：请求 chainId ≠ 配置 chainId 时，`Web3jChainQueryAdapter.assertChainIdMatches` 抛 `LEDGER_CHAIN_NOT_CONFIGURED` → 该错误码语义已覆盖"链未配置"，不新增错误码，design 明确说明复用。
- **[R6] 已确认的失败交易可通过闸门（安全审查修复）**：`isConfirmed` 原实现仅校验 blockNumber 与确认数，revert 交易（status=0x0）同样打包进块且确认数达标 → 已修复：isConfirmed 增加回执 status="0x1" 校验（与提现结算路径 isSuccessReceipt 一致），失败交易视为未确认。修复位置：`Web3jChainQueryAdapter.isConfirmed`。
- **[R7] 确认数覆盖可调低至 1 的操纵风险（技术债 DEVT-009）**：请求级 `requiredConfirmations` 可覆盖至 1（仅拦截 ≤0），配合自造交易可在 1 个确认（~12 秒）后入账，PoS 链存在 reorg 回滚导致的空充值风险。R4 决策允许调低覆盖为设计意图，本期不设下限；缓解：生产环境网关认证 + 对账模块以链上回执核销；后续变更可引入 `web3j.min-required-confirmations` 下限配置。
- **[R8] 金额/收款地址零核对（技术债 DEVT-010）**：闸门仅证明"存在一笔已确认交易"，不核对回执 value 与请求 amount、不核对收款方 to 是否为平台地址（proposal Non-Goal）；且 `queryTxReceipt` 构造 ChainTxReceipt 时 value 硬编码 0，对账模块需先修复 value 读取才有核对数据源。缓解：对账变更（README 规划）落地前依赖上游可信 + 网关认证。
- **[R9] 重复请求 RPC 消耗（设计决策 D5）**：同 bizNo 重复请求每次执行闸门（3 次 RPC）后幂等兜底，不做幂等预判短路（D5 明确接受"重复请求代价为一次 RPC"）；无认证下存在 RPC DoS 放大面，缓解同 DEVT-007（网关认证 + 限流）。

## Migration Plan

1. 代码发布顺序：common（错误码）→ application（DTO + 编排）→ interface（控制器）→ infrastructure（配置属性）→ starter（yaml）——同一版本内完成，不拆分兼容期。
2. 配置：`web3j.required-confirmations` 默认 12；发布前确认目标链的确认产出速度，必要时先调低并配合请求级覆盖。
3. 回滚策略：回滚代码后恢复旧行为（无确认检查）——回滚前必须先暂停上游充值调用，避免未确认入账回归；配置回滚仅需移除 yaml 项（属性有默认值，删除后回退默认 12 而非无限制）。
4. 观测：确认拒绝事件（`DEPOSIT_TX_NOT_CONFIRMED` / `DEPOSIT_CHAIN_QUERY_FAILED`）在错误码维度即可从统一响应与日志统计，上线初期重点观察拒绝率。

## Open Questions

- **tokenAddress 来源**：当前充值场景（ETH 原生转账）无代币合约地址；若后续支持 ERC-20 充值，需上游在请求中携带 `tokenAddress`（DTO 已预留字段），或在对账模块从回执 logs 解析——本变更不实现解析。
- **确认数的环境差异化**：prod/test 环境的 `required-confirmations` 可能不同（主网 12 vs 测试网 3），由各环境 yaml 覆盖，本变更不引入 profile 级默认逻辑。
