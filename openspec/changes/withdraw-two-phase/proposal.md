# 提案：提现两阶段改造（冻结 → 结算 → 解冻）

## Why（动机）

当前提现流程（`WithdrawAppServiceImpl.withdraw()`）是设计债 **DEVT-004** 的载体：它直接调用 `AccountAppService.changeAmountWithFundFlow(request)`（`FundFlowType = WITHDRAW`），而该方法的 WITHDRAW 分支在 `AccountAppServiceImpl.changeAmountWithFundFlowAtomic` 中调用 `AccountEntity.withdraw(amount)`——此方法从**冻结余额**扣减，但全流程没有任何冻结步骤，冻结余额恒为 0，导致 `Assert.isTrue(getFrozenBalance().compareTo(amount) > 0)` 必然抛出 `IllegalArgumentException`，**提现永远无法成功**。

同时，`AccountAppService.freezeBalance(uid, amount)` 与 `unfreezeBalance(uid, amount)` 是裸方法：只改余额、不记录资金流水、不过账总账，冻结/解冻事件在会计上完全不可见，破坏"流水与总账对账"的不变量。

上游真实流程为两阶段：**申请时冻结 → 审批通过结算 / 取消时解冻**。本提案将提现重构为标准两阶段流程，使余额、流水、总账三者在每个阶段保持一致，修复 DEVT-004。

## What Changes（设计方案要点）

### 提现重构为三个独立用例（限界上下文：withdraw）

- **冻结（freeze）**：`WithdrawAppServiceImpl.freeze()` — 将申请金额从可用余额转入冻结余额；记录 `FREEZE` 类型资金流水；过账总账凭证（`DEBIT USER_AVAILABLE` / `CREDIT USER_FROZEN`，业务类型 `FREEZE`）。
- **结算（withdraw/settle）**：`WithdrawAppServiceImpl.withdraw()` — 从冻结余额扣减；记录 `WITHDRAW` 类型资金流水；过账总账凭证（`DEBIT USER_FROZEN` / `CREDIT WITHDRAW_IN_TRANSIT`，业务类型 `WITHDRAW_ONCHAIN`）。
- **解冻（unfreeze）**：`WithdrawAppServiceImpl.unfreeze()` — 将冻结余额退回可用余额；记录 `UNFREEZE` 类型资金流水；过账总账凭证（`DEBIT USER_FROZEN` / `CREDIT USER_AVAILABLE`，业务类型 `UNFREEZE`）。

### 修复 `AccountEntity.withdraw()` 领域方法缺陷

- 移除多余的可用余额（`availableBalance`）检查——结算只校验冻结余额；
- 将裸 `Assert.isTrue(getFrozenBalance().compareTo(amount) > 0)` 替换为 `BizException(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE)`，比较语义修正为 `frozenBalance < amount` 才拒绝（即 `frozenBalance == amount` 为合法边界，允许全额结算）。

### 幂等与事务

- 每个阶段独立幂等：各自 `bizNo` 前置检查（`existsBizNo`）→ `REQUIRES_NEW` + `@Retryable(5)` 原子方法 → `fund_flow_t` 唯一索引 → `BizIdempotentException` 兜底；总账过账沿用 `postJournal` 自身幂等（相同 `bizNo`）。
- 过账失败不回滚余额与流水（沿用最终一致性模式，靠对账修复）。

### 清理死路径

- `changeAmountWithFundFlow` 的 WITHDRAW 分支移除，该方法仅保留 `DEPOSIT` 支持（**BREAKING**：WITHDRAW 类型调用将抛 `FUND_FLOW_TYPE_NOT_SUPPORT`）。提现改由独立用例承担。

## Capabilities（能力）

### New Capabilities（新增能力）

- `withdraw`：提现限界上下文的完整两阶段流程能力——冻结（申请锁定）、结算（审批后扣减）、解冻（取消/驳回释放），含各阶段资金流水与总账凭证，以及按阶段幂等。

### Modified Capabilities（修改能力）

- `account`：`AccountAppService.changeAmountWithFundFlow` 的行为需求变更——不再支持 `WITHDRAW` 类型（仅保留 `DEPOSIT`）；`AccountEntity.withdraw()` 的校验语义修正（只校验冻结余额、错误码与边界修正）。

## Impact（影响）

- **otx-domain**：`AccountEntity.withdraw()` 修复（错误码 + 边界语义 + 移除可用余额检查）；`LedgerBizTypeEnum` 新增 `FREEZE` / `UNFREEZE` 两个业务类型值。
- **otx-application**：`WithdrawAppService` / `WithdrawAppServiceImpl` 重构为三个独立用例（冻结/结算/解冻，各自原子方法 + 幂等入口）；新增 `WithdrawRequestDTO`（`application/withdraw/dto/request/`，替代借用自 deposit 包的 `ChangeAmountRequest`）；`AccountAppServiceImpl.changeAmountWithFundFlow` 移除 WITHDRAW 分支。
- **otx-interface**：新增 `WithdrawController`，暴露 `POST /withdraw/freeze`、`POST /withdraw/settle`、`POST /withdraw/unfreeze` 三个端点。
- **otx-common**：无改动（现有错误码已覆盖：`INSUFFICIENT_BALANCE` / `INSUFFICIENT_FROZEN_BALANCE` / `FREEZE_AMOUNT_INVALID` / `UNFREEZE_AMOUNT_INVALID` / `WITHDRAW_AMOUNT_INVALID` / `FUND_FLOW_TYPE_NOT_SUPPORT` 等，**不新增错误码**）。
- **数据库**：无 schema 变更（`fund_flow_t.biz_no` 唯一索引、`ledger_entry_t` 联合唯一索引均复用既有设施）。
- **测试**：`AccountEntityTest` 扩展边界用例；`WithdrawAppServiceImplTest` 重写为三用例；`AccountAppServiceImplTest` 同步 WITHDRAW 分支变更；新增端到端集成测试覆盖三阶段 + 幂等 + 并发。

## 非目标（Non-Goals）

- **不引入 `SETTLE` 资金流水类型**（设计债 DEVF-003：结算阶段沿用现有 `WITHDRAW` 类型，`FundFlowTypeEnum` 不在本次变更范围）。
- **不做链上广播（broadcast）与链上确认（confirmation）**：结算后链上出金、确认数校验属于独立的 `tx-broadcast-orchestration` 变更，本变更只负责链下账务。
- **不新增 `BizErrorEnum` 错误码**：现有枚举已覆盖全部所需错误分支。
- **不改造 `FundFlowEntity` 结构**（DEVF-002 贫血体不在范围）：流水快照字段复用现有单一 `balanceBefore` / `balanceAfter`，其语义为本笔资金变动科目（可用或冻结）的变动前后余额。
- **不引入领域事件总线**（DEVT-001 不在范围）：跨聚合一致性继续沿用现有同步调用模式。
