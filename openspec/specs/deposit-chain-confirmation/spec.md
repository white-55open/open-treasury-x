# deposit-chain-confirmation 规范

## 目的

充值入账链上确认（Deposit Chain Confirmation）上下文为充值入账增加**链上证据闸门**：入账请求必须携带链上交易证据（chainId + chainTxHash），系统在入账前验证链上交易已达到安全确认数，未确认或查询失败时拒绝入账（fail-safe），确认通过后将链字段写入总账凭证，支撑后续"链上与链下对账"能力。

不属于本上下文：充值入账的余额/流水/凭证编排（→ account / fundflow / ledger）、提现侧链上确认（→ tx-broadcast）、链上交易广播（→ tx-broadcast）。

## 需求

### 需求:充值请求必须携带链上证据

充值入账请求必须（MUST）携带链上交易证据：`chainId`（区块链 ID）与 `chainTxHash`（链上交易哈希）。`DepositAppServiceImpl.deposit()` 在入账前必须（MUST）先校验这两个字段，任一为空或为 null 时必须（MUST）拒绝入账，抛出 `BizException`，业务错误码必须（MUST）为 `DEPOSIT_CHAIN_INFO_MISS`，余额、流水、凭证均不得产生。

#### 场景:请求缺失 chainId
- **当** 充值请求的 `chainId` 为 null 或空白字符串
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_CHAIN_INFO_MISS`
- **那么** 账户余额不得变化，不得产生资金流水与总账凭证

#### 场景:请求缺失 chainTxHash
- **当** 充值请求的 `chainTxHash` 为 null 或空白字符串
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_CHAIN_INFO_MISS`
- **那么** 账户余额不得变化，不得产生资金流水与总账凭证

### 需求:已确认的链上交易才能入账

充值入账前必须（MUST）（MUST）通过 `ChainQueryPort.isConfirmed(chainId, chainTxHash, requiredConfirmations)` 校验链上确认数。当返回 `true`（确认数 ≥ 要求值）时，系统才允许继续执行既有入账流程：增加用户可用余额 → 记录资金流水（IN/DEPOSIT）→ 总账过账（DEBIT DEPOSIT_IN_TRANSIT / CREDIT USER_AVAILABLE）。

#### 场景:链上交易已确认时充值成功
- **当** 充值请求携带已确认的 `chainTxHash`，`isConfirmed` 返回 true
- **那么** 系统必须完成：用户余额增加 → 资金流水记录（IN/DEPOSIT）→ 总账凭证过账
- **那么** 系统必须返回 `bizNo`，凭证状态必须为 POSTED

#### 场景:确认检查通过后凭证携带链上证据
- **当** 一笔已确认的充值成功入账，请求携带 `chainId`、`chainTxHash`
- **那么** 凭证的 `chainId` 与 `chainTxHash` 必须等于请求值
- **那么** 凭证的 `blockNumber` 必须等于链上回执中的区块高度

### 需求:未确认的链上交易必须拒绝入账

当链上交易尚未达到安全确认数（`isConfirmed` 返回 `false`，包括链上查无该交易、交易无区块高度等情形）时，系统必须（MUST）拒绝入账，抛出 `BizException`，业务错误码必须（MUST）为 `DEPOSIT_TX_NOT_CONFIRMED`。余额、流水、凭证均不得产生。

#### 场景:确认数不足时拒绝充值
- **当** 充值请求携带的 `chainTxHash` 在链上存在但确认数低于要求值，`isConfirmed` 返回 false
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_TX_NOT_CONFIRMED`
- **那么** 账户余额不得变化，不得产生资金流水与总账凭证

#### 场景:链上查无该交易时拒绝充值
- **当** 充值请求携带的 `chainTxHash` 在链上不存在（如伪造哈希），`isConfirmed` 返回 false
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_TX_NOT_CONFIRMED`

### 需求:链上查询失败时必须安全拒绝入账

当链上查询因 RPC 节点不可用等原因失败（`Web3jRpcException`）时，系统不得放行资金，必须（MUST）将异常转换为 `BizException`，业务错误码必须（MUST）为 `DEPOSIT_CHAIN_QUERY_FAILED`，拒绝入账。余额、流水、凭证均不得产生。

#### 场景:RPC 不可用时拒绝充值
- **当** 链上确认检查过程中所有 RPC 节点均不可用，抛出 `Web3jRpcException`
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_CHAIN_QUERY_FAILED`
- **那么** 账户余额不得变化，不得产生资金流水与总账凭证

### 需求:确认数要求必须可配置且可请求级覆盖

系统必须（MUST）提供配置项 `web3j.required-confirmations` 作为默认安全确认数（默认值 12）。当请求未携带 `requiredConfirmations` 时使用该默认值；请求携带 `requiredConfirmations` 时以请求值为准（`isConfirmed` 的第三个参数）。请求级值必须（MUST）为正整数，否则拒绝入账（业务错误码 `DEPOSIT_CHAIN_INFO_MISS`）。

#### 场景:未携带覆盖值时使用默认确认数
- **当** 充值请求未携带 `requiredConfirmations`，配置 `web3j.required-confirmations` 为 12
- **那么** 系统必须以 12 作为所需确认数调用 `isConfirmed(chainId, chainTxHash, 12)`

#### 场景:请求级覆盖默认确认数
- **当** 充值请求携带 `requiredConfirmations = 6`
- **那么** 系统必须以 6 作为所需确认数调用 `isConfirmed(chainId, chainTxHash, 6)`，忽略默认值

#### 场景:请求级覆盖值为非法正整数
- **当** 充值请求携带的 `requiredConfirmations` 为 null、0 或负数
- **那么** 系统必须拒绝入账，抛 `BizException`，业务错误码必须为 `DEPOSIT_CHAIN_INFO_MISS`

### 需求:充值幂等语义保持不变

`bizNo` 唯一索引仍是幂等兜底：同一 `bizNo` 重复调用充值接口时，链确认检查仍会执行，但余额变更与流水记录必须（MUST）（MUST）走幂等路径，返回原 `bizNo`，不得重复增加余额、不得产生重复流水与重复凭证。

#### 场景:同一 bizNo 重复充值不重复入账
- **当** 使用已成功入账的 `bizNo` 重复发起充值请求，且链确认检查通过
- **那么** 系统必须返回原 `bizNo`
- **那么** 账户余额不得再次变化，资金流水与总账凭证均不得新增

## 领域事件

### 应发布事件（应然）

| 事件名 | 触发时机 | 载荷 |
|--------|----------|------|
| `DepositConfirmed` | 链确认闸门通过后 | `uid, bizNo, chainId, chainTxHash, confirmations, occurredAt` |

### 当前发布事件（实然）

**无。** 本期不发布任何领域事件，与 account/ledger 上下文一致。

## 错误码契约

本规范所有错误码均引自 `io.github.open55.otx.common.exception.BizErrorEnum`，规范不复制枚举值清单（单一事实源在代码）。下表给出每个业务错误码在本上下文中的触发场景。

| 业务错误码 | 在本上下文的触发场景 |
|------------|----------------------|
| `DEPOSIT_CHAIN_INFO_MISS` | chainId/chainTxHash 缺失或 requiredConfirmations 非法 |
| `DEPOSIT_TX_NOT_CONFIRMED` | 链上交易未达安全确认数 |
| `DEPOSIT_CHAIN_QUERY_FAILED` | 链上查询失败（RPC 不可用等），fail-safe 拒绝入账 |
| `LEDGER_CHAIN_NOT_CONFIGURED` | 请求 chainId 与配置的单链 chainId 不匹配（复用既有错误码） |

## 入站端口（Inbound Port）接口契约

```java
public interface DepositAppService {
    String deposit(DepositRequestDTO request);   // 入账前先过链确认闸门
}
```

### 入站 DTO

```java
public class DepositRequestDTO extends ChangeAmountRequest {
    private String chainId;              // 区块链 ID（必填）
    private String chainTxHash;          // 链上交易哈希（必填）
    private Integer requiredConfirmations; // 可选，覆盖默认确认数
    private String tokenAddress;         // 可选，代币合约地址
}
```

## 出站端口（Outbound Port）接口契约

本上下文复用 ledger 上下文的 `ChainQueryPort`（`isConfirmed` / `queryTxReceipt` / `currentBlockNumber`），不新增出站端口。
