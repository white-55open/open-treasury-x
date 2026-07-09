## 为什么

当前系统的充值（Deposit）和提现（Withdraw）业务流程只完成了「账户余额变更 + 资金流水记录」，没有将业务事件过账到总账模块（Ledger）。这意味着系统只有流水明细，没有符合复式记账规范的会计凭证，无法支撑财务报表、审计追溯和链上对账。

Ledger 模块（聚合根 `LedgerJournalEntity`  + 复式记账不变量）和 Web3j 链上查询适配器（`Web3jChainQueryAdapter`）均已实现但处于闲置状态。本变更填补业务层与总账层之间的缺口，让充值/提现的每一次资金变动都在总账中产生一张对应的会计凭证。

## 变更内容

### 新增能力

- **充值自动过账**：`DepositAppServiceImpl.deposit()` 在完成余额变更和资金流水记录后，自动调用 `LedgerAppService.postJournal()` 生成总账凭证
- **提现自动过账**：`WithdrawAppServiceImpl.withdraw()` 在完成余额变更和资金流水记录后，自动调用 `LedgerAppService.postJournal()` 生成总账凭证

### 修改能力

- **`ChangeAmountRequest`**：新增 `currency` 字段（String，默认 "USDT"），供过账时填充凭证币种

### 会计分录规则

```
充值（DEPOSIT）→ 业务类型 DEPOSIT_ONCHAIN
  借方  DEPOSIT_IN_TRANSIT  金额  无 uid        （在途资产增加）
  贷方  USER_AVAILABLE      金额  用户 uid       （用户余额增加）

提现（WITHDRAW）→ 业务类型 WITHDRAW_ONCHAIN
  借方  USER_AVAILABLE      金额  用户 uid       （用户余额减少）
  贷方  WITHDRAW_IN_TRANSIT 金额  无 uid        （在途负债增加）
```

### 事务与一致性策略

- 余额变更 + 资金流水记录在同一事务（`REQUIRES_NEW`），总账过账在另一事务（`REQUIRES_NEW`）
- 跨聚合一致性采用**最终一致性**：余额与流水先写入，总账后写入。若过账失败，余额和流水已持久化，账本留空，后续通过对账修复
- 符合 DDD 约束：一个事务只修改一个聚合（Account 聚合 / LedgerJournal 聚合）

## 功能 (Capabilities)

### 新增功能
- `business-ledger-integration`: 充值与提现业务流程自动过账到总账模块，包含分录映射规则、幂等处理、事务边界

### 修改功能
- `ledger`: 当前 `POST /ledger/journals` 端点行为不变，但总账凭证的新增来源从「仅通过 API 手动调用」扩展为「API 手动 + 充值/提现自动触发」

## 影响

| 层 | 变更 |
|---|---|
| `otx-application` | `ChangeAmountRequest` 新增 `currency` 字段；`DepositAppServiceImpl` 增加过账调用；`WithdrawAppServiceImpl` 增加过账调用；可能新增映射工具类将 `ChangeAmountRequest` 转换为 `PostJournalRequestDTO` |
| 其他层 | 无变更。Domain / Infrastructure / Interface 均不受影响 |
