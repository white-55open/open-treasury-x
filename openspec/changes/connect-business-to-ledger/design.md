## 上下文

当前 `DepositAppServiceImpl` 和 `WithdrawAppServiceImpl` 是极薄的委托层，直接调用 `AccountAppService.changeAmountWithFundFlow()` 完成「余额变更 + 资金流水记录」。总账模块（Ledger）已完整实现（聚合根 `LedgerJournalEntity`、复式记账不变量、`LedgerAppService.postJournal()`），但没有任何业务流触发过账。

Web3j 适配器也已实现（`Web3jChainQueryAdapter`）但同样未被调用。本变更暂不接入链上查询，仅打通业务→总账这一环。

## 目标 / 非目标

**目标：**

- `DepositAppServiceImpl.deposit()` 自动调用 `LedgerAppService.postJournal()` 生成总账凭证
- `WithdrawAppServiceImpl.withdraw()` 自动调用 `LedgerAppService.postJournal()` 生成总账凭证
- `ChangeAmountRequest` 新增 `currency` 字段
- 每次充值/提现产生一条 `LedgerJournal`（DRAFT → POSTED）及其对应分录
- 完整单元测试覆盖

**非目标：**

- ❌ 不接入 `ChainQueryPort` / web3j（过账前不查链上确认）
- ❌ 不创建新的编排层（逻辑放在 `DepositAppServiceImpl` / `WithdrawAppServiceImpl` 内）
- ❌ 不改 `AccountAppService` 的 `changeAmountWithFundFlowAtomic` 方法
- ❌ 不改数据库 schema、不改领域层代码
- ❌ 不发布领域事件（`JournalPosted` 等暂不发布）

## 决策

### 决策 1：过账调用放在 `DepositAppServiceImpl` / `WithdrawAppServiceImpl`

不放在 `AccountAppService` 内部，原因：

- `AccountAppService` 当前是系统最核心的服务（含重试逻辑），不新增依赖
- 两事务（余额流水一个 REQUIRES_NEW，过账另一个 REQUIRES_NEW）符合 DDD 约束
- 为后续在过账前插入链上确认检查（`ChainQueryPort.isConfirmed()`）预留位置

### 决策 2：两事务 + 最终一致性

```
DepositAppServiceImpl.deposit()
  ├─ step ①: accountAppService.changeAmountWithFundFlow(req)  [REQUIRES_NEW]
  │    改余额 + 记资金流水（Account 聚合）
  │
  └─ step ②: ledgerAppService.postJournal(buildRequest(req))  [REQUIRES_NEW]
       过账（LedgerJournal 聚合）
```

- 若 step ① 成功、step ② 失败：余额和流水已写入，账本未记。当前阶段接受此状态，后续通过对账/补偿任务修复
- 若 step ① 失败：step ② 不会执行，系统状态不变
- 幂等保证：`LedgerAppService.postJournal()` 内部已通过 `bizNo` + 联合唯一索引实现幂等，重复调用安全

### 决策 3：映射逻辑内联在服务类中

不创建单独的 Mapper 类，而是在 `DepositAppServiceImpl` / `WithdrawAppServiceImpl` 中各加一个 private 方法构建 `PostJournalRequestDTO`。理由：

- 映射规则简单（两种业务类型各两个分录），不值得独立类
- 映射本质上是编排逻辑（决定会计分录），归属应用层
- 后续若映射规则变复杂，再提取到 `LedgerAssembler` 扩展

### 决策 4：`ChangeAmountRequest` 新增 `currency` 字段

- 类型：`String`
- 默认值：`"USDT"`（当前业务场景仅 USDT）
- `DepositController` / `WithdrawController` 的请求体不要求调用方传 `currency`，由前端或客户端按需填充
- 不做必填校验，为空时过账默认 `"USDT"`（与 `LedgerJournalEntity.create()` 的 `LEDGER_CURRENCY_EMPTY` 校验配合——若 currency 为空，过账时会抛异常）

### 决策 5：PostJournalRequestDTO 字段填充规则

| PostJournalRequestDTO 字段 | 值来源 |
|---|---|
| `bizNo` | `request.getBizNo()` 复用资金流水 bizNo |
| `bizType` | DEPOSIT → `DEPOSIT_ONCHAIN`；WITHDRAW → `WITHDRAW_ONCHAIN` |
| `currency` | `request.getCurrency()`，默认 "USDT" |
| `postingDate` | `LocalDate.now()` |
| `description` | "充值" / "提现" + bizNo |
| `chainId` / `chainTxHash` / `blockNumber` / `tokenAddress` | null（后续链上确认阶段填充） |
| `entries` | 见分录映射表 |

### 决策 6：会计分录映射

```
充值（FundFlowTypeEnum.DEPOSIT）→ bizType = LedgerBizTypeEnum.DEPOSIT_ONCHAIN
  Entry[0]:  DEBIT  DEPOSIT_IN_TRANSIT  amount  uid=null   counterparty=null  balanceAfter=null
  Entry[1]:  CREDIT USER_AVAILABLE      amount  uid=req.uid counterparty=null  balanceAfter=null

提现（FundFlowTypeEnum.WITHDRAW）→ bizType = LedgerBizTypeEnum.WITHDRAW_ONCHAIN
  Entry[0]:  DEBIT  USER_AVAILABLE        amount  uid=req.uid counterparty=null  balanceAfter=null
  Entry[1]:  CREDIT WITHDRAW_IN_TRANSIT   amount  uid=null   counterparty=null  balanceAfter=null
```

`LedgerEntryEntity` 的 `balanceAfter` 字段传 null——当前没有上下文计算每个科目的实时余额，后续对账阶段再补充。

### 决策 7：异常处理策略

- step ② 过账失败时，不阻断 step ① 的成功结果（不抛异常）
- 日志记录 `WARN` 级别：`"Journal posting failed for bizNo={}, balance and fund flow already committed"`
- 调用方（Controller）仍返回成功——因为用户余额已变更、流水已记录，业务已完成
- 账本缺失的记录后续通过定时对账任务修复

## 完整流程

```
POST /deposit
  │
  ├─ DepositController.deposit(req)
  │   └─ DepositAppService.deposit(req)
  │
  ├─ [1] setFundFlowType(DEPOSIT) + setCurrency("USDT")
  │
  ├─ [2] accountAppService.changeAmountWithFundFlow(req)
  │       ┌────────────────────────────────────────┐
  │       │  REQUIRES_NEW + @Retryable             │
  │       │  ├─ account.deposit(amount)            │
  │       │  ├─ fundFlowAppService.record(...)     │
  │       │  └─ accountRepo.update(account)        │
  │       └────────────────────────────────────────┘
  │
  ├─ [3] buildPostJournalRequest(req, DEPOSIT_ONCHAIN,
  │       [DEBIT DEPOSIT_IN_TRANSIT, CREDIT USER_AVAILABLE])
  │
  └─ [4] ledgerAppService.postJournal(journalReq)
          ┌────────────────────────────────────────┐
          │  REQUIRES_NEW + @Retryable             │
          │  ├─ existsByBizNo → 幂等返回            │
          │  ├─ journalRepo.save + entryRepo.save  │
          │  ├─ journal.post()                     │
          │  └─ journalRepo.update                 │
          └────────────────────────────────────────┘
```

## 风险 / 权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| step ② 过账失败导致账本缺失 | 总账与流水不一致 | 日志记录 + 后续对账任务修复；当前阶段接受最终一致性 |
| 过账延迟（两事务串行） | 响应时间增加 | 过账事务仅 DB 写操作，微秒级，对总延迟影响可忽略 |
| `currency` 为空导致过账异常 | 过账抛 `LEDGER_CURRENCY_EMPTY` | 日志警告该场景，提示调用方补充 currency；异常不影响用户余额 |
| 映射规则写死后难以扩展 | 新增业务类型需改映射代码 | 当前仅两种业务类型，映射逻辑简单；复杂度上升时提取到独立类 |
