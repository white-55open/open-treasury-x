# tx-broadcast 规范

## 目的

链上交易广播（Tx Broadcast）上下文提供提现交易的**签名与广播编排**能力：在资金已冻结的前提下构造链上交易、经可插拔 `SignerPort` 签名（私钥绝不进入 OTX 核心）、经 `TxBroadcastPort` 广播上链、持久化提现请求状态机（PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED），并在链上确认达标后完成结算过账；链上失败或取消时解冻资金。广播成功即创建 DRAFT 凭证留痕，确认后过账（激活 ledger 的 DRAFT 状态机）。

不属于本上下文：提现冻结/结算/解冻的链下账务（→ withdraw）、复式记账凭证（→ ledger）、充值入账的链上确认（→ deposit-chain-confirmation）。

## 需求

### 需求:提现交易必须经签名与广播上链

提现应用服务（`WithdrawAppService.broadcast()`）在资金已冻结（依赖 withdraw 上下文的冻结能力）的前提下，必须（MUST）构造链上交易并通过 `SignerPort` 签名、`TxBroadcastPort` 广播，将链上交易哈希（txHash）持久化后返回。禁止直接扣减余额或立即过账。

- 广播成功时：提现请求状态必须为 BROADCASTED，持久化 chainId、toAddress、tokenAddress、txHash
- 广播成功时：必须创建一条状态为 DRAFT 的总账凭证，携带 chainId 与 chainTxHash，但**不得**调用 post() 过账
- 广播成功时：不得扣减账户余额、不得产生资金流水（资金仍处于冻结中）

#### 场景:广播成功返回交易哈希
- **当** 用户对已冻结的提现请求调用 broadcast，且签名与广播均成功返回 txHash
- **那么** 系统必须返回该 txHash
- **那么** 系统中必须存在 bizNo 对应的提现请求记录，状态为 BROADCASTED，且 txHash 与链上返回值一致
- **那么** 系统中必须存在 bizNo 对应的总账凭证，状态为 DRAFT，chainTxHash 与 txHash 一致
- **那么** 账户余额必须保持不变（资金仍冻结，无扣款流水）

#### 场景:广播成功时凭证不过账
- **当** 一笔提现交易广播成功并创建 DRAFT 凭证后，查询该凭证
- **那么** 凭证状态必须为 DRAFT 而非 POSTED

### 需求:签名必须通过可插拔 SignerPort 端口完成

系统必须（MUST）在领域层定义 `SignerPort` 出站端口，提现应用服务只依赖该端口完成交易签名，OTX 核心不得接触或存储任何私钥。签名适配器必须（MUST）通过配置选择（开发环境为本地 keystore，生产环境对接外部签名设施）。

#### 场景:应用层仅依赖 SignerPort 接口
- **当** 提现应用服务执行签名步骤
- **那么** 签名必须经由 SignerPort 接口调用完成，不得在应用层或领域层直接构造私钥、不得读取任何密钥文件

#### 场景:签名失败抛出业务错误
- **当** SignerPort 签名抛出异常
- **那么** 系统必须抛出 BizException，错误码为 TX_SIGN_FAILED
- **那么** 提现请求状态必须为 FAILED，资金保持冻结

### 需求:广播必须通过 TxBroadcastPort 端口完成

系统必须（MUST）在领域层定义 `TxBroadcastPort` 出站端口，广播已签名交易并返回交易哈希；端口还必须（MUST）提供获取指定链与发送方当前 nonce 的辅助方法。

#### 场景:应用层调用 TxBroadcastPort.broadcast
- **当** 提现应用服务广播一笔已签名交易
- **那么** 广播必须经由 TxBroadcastPort.broadcast 调用完成，返回 BroadcastResult 且携带非空 txHash

#### 场景:获取当前 nonce
- **当** 应用层需要为提现交易构造 nonce
- **那么** 系统必须支持调用 txBroadcastPort.currentNonce(chainId, fromAddress) 返回当前 nonce

### 需求:广播失败不产生凭证且资金保持冻结

广播失败（RPC 拒绝、超时、网络异常）时，系统必须（MUST）（MUST）抛出业务错误，**不创建**总账凭证（凭证在广播成功后才创建），提现请求状态标记为 FAILED；资金保持冻结，由调用方通过取消路径（unfreeze）释放。

#### 场景:广播失败抛出 TX_BROADCAST_FAILED
- **当** TxBroadcastPort.broadcast 抛出异常或返回失败
- **那么** 系统必须抛出 BizException，错误码为 TX_BROADCAST_FAILED
- **那么** 提现请求状态必须为 FAILED
- **那么** 系统中不得存在该 bizNo 对应的总账凭证
- **那么** 账户余额必须保持不变（冻结资金未释放，等待取消路径处理）

### 需求:结算必须待链上确认后执行

系统必须（MUST）提供 `confirmAndSettle(bizNo)` 结算用例：调用 `ChainQueryPort.isConfirmed(chainId, txHash, requiredConfirmations)` 判断链上确认数；确认达标时依次执行扣减冻结余额、记录提现流水、将 DRAFT 凭证过账为 POSTED，并将提现请求状态置为 SETTLED。

- 扣减冻结余额与记录流水必须复用既有原子能力（同一 REQUIRES_NEW 事务）
- 凭证过账必须复用既有幂等过账能力（postJournalByBizNo），凭证状态变更必须通过聚合根 post() 方法

#### 场景:确认达标完成结算
- **当** 调用 confirmAndSettle(bizNo) 且该提现的链上确认数 ≥ 配置的必填确认数
- **那么** 系统必须扣减用户冻结余额（金额等于提现金额）
- **那么** 系统必须产生一条金额等于提现金额的资金流水（OUT/WITHDRAW）
- **那么** bizNo 对应的凭证状态必须变为 POSTED
- **那么** 提现请求状态必须变为 SETTLED

### 需求:未确认的结算必须被拒绝且可重试

链上确认数不足时，结算必须（MUST）被拒绝并抛出特定业务错误，调用方可在确认数达标后重试；重复结算必须（MUST）幂等返回相同结果。

#### 场景:确认数不足拒绝结算
- **当** 调用 confirmAndSettle(bizNo) 且链上确认数 < 必填确认数
- **那么** 系统必须抛出 BizException，错误码为 TX_NOT_CONFIRMED_YET
- **那么** 账户余额、凭证状态、提现请求状态均不得发生变化

#### 场景:确认数达标后重试成功
- **当** 第一次 confirmAndSettle 因确认数不足被拒绝，链上确认数达标后再次调用
- **那么** 系统必须完成结算（扣款、流水、凭证 POSTED、状态 SETTLED）

### 需求:结算必须幂等

对同一 bizNo 的重复结算请求（无论是否已结算）必须（MUST）返回相同结果，不得重复扣款、不得重复产生流水或重复过账。

#### 场景:已结算后重复结算返回相同结果
- **当** 一笔提现已 SETTLED 后再次调用 confirmAndSettle(bizNo)
- **那么** 系统必须返回与原结算相同的成功结果
- **那么** 账户冻结余额只扣减一次、流水只有一条、凭证只有一张且状态为 POSTED

#### 场景:并发结算仅生效一次
- **当** 两个线程同时对同一 bizNo 调用 confirmAndSettle
- **那么** 系统必须保证仅一次完整结算生效，另一方获得幂等成功或乐观锁重试后成功
- **那么** 最终冻结余额扣减一次、流水一条、凭证一张

### 需求:链上失败必须解冻资金

链上确认阶段发现交易失败（回执状态为失败）或交易缺失时，系统必须（MUST）将提现请求标记为 FAILED，释放冻结资金（调用取消路径 unfreeze），并保留 DRAFT 凭证作为审计痕迹（凭证不冲销——reverse() 仅适用于 POSTED 凭证，DRAFT 残留由后续对账变更处理）。

#### 场景:链上交易失败解冻
- **当** confirmAndSettle 确认时链上回执状态为失败（0x0）
- **那么** 系统必须抛出 BizException，错误码为 TX_CHAIN_FAILED
- **那么** 提现请求状态必须变为 FAILED
- **那么** 系统必须将冻结金额释放回可用余额并记录解冻流水
- **那么** 该 bizNo 的 DRAFT 凭证必须保留（作为审计痕迹，不得删除）

#### 场景:链上交易缺失解冻
- **当** confirmAndSettle 确认时链上查不到该交易回执
- **那么** 系统必须抛出 BizException，错误码为 TX_CHAIN_FAILED
- **那么** 提现请求状态必须变为 FAILED，冻结资金必须释放

### 需求:必填确认数必须可配置

系统必须（MUST）通过配置（`otx.chain-tx.required-confirmations`）指定结算所需的链上确认数，结算时以配置值作为确认阈值。

#### 场景:配置确认数生效
- **当** 配置 required-confirmations=12 且链上确认数为 11 时调用 confirmAndSettle
- **那么** 系统必须抛出 TX_NOT_CONFIRMED_YET
- **当** 链上确认数达到 12 时再次调用
- **那么** 系统必须完成结算

### 需求:取消提现必须解冻资金

系统必须（MUST）提供 `cancelWithdraw(bizNo)` 取消用例：对未结算（BROADCASTED/FAILED 等非 SETTLED 状态）的提现请求，将冻结金额释放回可用余额，提现请求状态置为 CANCELLED。解冻能力复用 withdraw 上下文提供的 unfreeze 能力。

#### 场景:取消已广播未确认的提现
- **当** 调用 cancelWithdraw(bizNo) 且提现请求处于 BROADCASTED 状态
- **那么** 系统必须将冻结金额释放回可用余额并记录解冻流水
- **那么** 提现请求状态必须变为 CANCELLED

#### 场景:已结算的提现不可取消
- **当** 调用 cancelWithdraw(bizNo) 且提现请求处于 SETTLED 状态
- **那么** 系统必须抛出 BizException，错误码为 WITHDRAW_REQUEST_STATUS_INVALID
- **那么** 账户余额不得发生变化

## 聚合根（Aggregate Root）与聚合边界（Aggregate Boundary）

### 聚合根：WithdrawRequestEntity

每个聚合根对应一笔提现请求（`withdraw_request_t`），承载链上广播编排状态。聚合根保证状态机（PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED）合法性：`markBroadcasted` 仅允许 PENDING/FAILED、`markSettled` 仅允许 BROADCASTED、`markFailed` 仅允许 PENDING/BROADCASTED、`cancel` 仅允许 PENDING/BROADCASTED/FAILED，非法转移抛 `WITHDRAW_REQUEST_STATUS_INVALID`。

### 聚合边界

- **WithdrawRequestEntity**（聚合根）：uid、bizNo（唯一）、amount、currency、chainId、toAddress、tokenAddress、txHash、status
- **仓储**：`WithdrawRequestRepo`（save/findByBizNo/existsByBizNo/update），只操作聚合根
- **出站端口**：`SignerPort`（签名，私钥外部化）、`TxBroadcastPort`（广播 + nonce 辅助）、`ChainQueryPort`（链上确认，属 ledger 上下文）

## 领域事件

### 应发布事件（应然）

| 事件名 | 触发时机 | 载荷 |
|--------|----------|------|
| `WithdrawBroadcasted` | 广播成功并持久化 BROADCASTED 后 | `bizNo, txHash, chainId, amount, occurredAt` |
| `WithdrawSettled` | 结算用例成功后 | `bizNo, txHash, amount, occurredAt` |
| `WithdrawBroadcastFailed` | 广播失败置 FAILED 后 | `bizNo, chainId, reason, occurredAt` |

### 当前发布事件（实然）

**无。** 本期不发布任何领域事件，跨聚合一致性通过应用层同步编排 + 最终一致性（对账修复）实现。

## 错误码契约

本规范所有错误码均引自 `io.github.open55.otx.common.exception.BizErrorEnum`，规范不复制枚举值清单（单一事实源在代码）。下表给出每个业务错误码在本上下文中的触发场景。

| 业务错误码 | 在本上下文的触发场景 |
|------------|----------------------|
| `TX_SIGN_FAILED` | SignerPort 签名异常（keystore 缺失/密码错误等） |
| `TX_BROADCAST_FAILED` | 广播失败（RPC 拒绝/超时/网络异常） |
| `TX_NOT_CONFIRMED_YET` | 结算时链上确认数 < 必填确认数（可重试） |
| `TX_CHAIN_FAILED` | 链上交易失败（回执 0x0）或交易缺失 |
| `WITHDRAW_REQUEST_NOT_FOUND` | 按 bizNo 查不到提现请求 |
| `WITHDRAW_REQUEST_STATUS_INVALID` | 状态非法转移/非法操作（如 SETTLED 后再取消） |
| `INSUFFICIENT_FROZEN_BALANCE` | 广播时账户冻结余额不足（资金未先冻结） |
| `LEDGER_CHAIN_NOT_CONFIGURED` | 请求 chainId 未在 web3j 配置中注册 |

## 入站端口（Inbound Port）接口契约

```java
public interface WithdrawAppService {
    // 链下账务（→ withdraw 上下文）
    String freeze(WithdrawRequestDTO request);
    String withdraw(WithdrawRequestDTO request);
    String unfreeze(WithdrawRequestDTO request);
    // 链上广播编排（本上下文）
    WithdrawBroadcastResponseDTO broadcast(WithdrawRequestDTO request);
    WithdrawSettleResponseDTO confirmAndSettle(String bizNo);
    WithdrawStatusResponseDTO cancelWithdraw(String bizNo);
    WithdrawStatusResponseDTO queryStatus(String bizNo);
}
```

### 出站端口（Outbound Port）接口契约

```java
public interface SignerPort {
    SignedTx sign(SignRequest request);   // 签名实现可插拔，私钥绝不进入 OTX 核心
}

public interface TxBroadcastPort {
    BroadcastResult broadcast(SignedTx signedTx);
    BigInteger currentNonce(String chainId, String fromAddress);
}
```
