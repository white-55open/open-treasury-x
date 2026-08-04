## MODIFIED Requirements

### Requirement: 动账并落流水必须满足幂等性与跨聚合落库

系统必须（MUST）通过入站端口 `AccountAppService.changeAmountWithFundFlow(request)` 一次性完成"修改账户余额 + 在 fundflow 上下文记录一条流水"。该方法必须满足：

- 入参 `bizNo` 为幂等键：同一 `bizNo` 重复调用必须返回相同结果，不得重复扣减或重复落库。
- 入参 `fundFlowType` 仅支持 `DEPOSIT`；`WITHDRAW` / `FREEZE` / `UNFREEZE` / 其他类型必须抛 `FUND_FLOW_TYPE_NOT_SUPPORT`（提现改由 withdraw 上下文的两阶段用例承担，不再复用本方法）。
- `DEPOSIT` 对应入账（`direction = IN`），账户执行 `deposit`。
- 落流水时 `balanceBefore` / `balanceAfter` 必须分别为动账前/后的 `availableBalance`。
- 账户持久化必须配合乐观锁：若乐观锁冲突（`@Version` 失配）必须抛 `OptimisticLockException`，并由 `@Retryable` 重试至多 5 次。

#### Scenario: 首次动账并落流水（DEPOSIT）

- **WHEN** 调用 `changeAmountWithFundFlow` 且 `bizNo` 未被使用过，`fundFlowType = DEPOSIT`
- **THEN** 账户可用余额必须增加入参 `amount`；fundflow 必须落一条 `direction = IN`、`type = DEPOSIT` 的流水，`balanceBefore` / `balanceAfter` 与动账前后一致

#### Scenario: 重复 bizNo 幂等

- **WHEN** 调用 `changeAmountWithFundFlow` 且 `bizNo` 已被成功使用过
- **THEN** 系统必须返回原 `bizNo`，账户余额不得再次变化，fundflow 不得新增重复流水

#### Scenario: WITHDRAW 类型被拒绝

- **WHEN** 调用 `changeAmountWithFundFlow` 且 `fundFlowType = WITHDRAW`
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `FUND_FLOW_TYPE_NOT_SUPPORT`，账户余额与 fundflow 均不得变化

#### Scenario: 不识别的资金类型

- **WHEN** 调用 `changeAmountWithFundFlow` 且 `fundFlowType` 为 `FREEZE` / `UNFREEZE` / 其他非支持值
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `FUND_FLOW_TYPE_NOT_SUPPORT`

#### Scenario: 入参缺失

- **WHEN** 调用 `changeAmountWithFundFlow` 且任一必填入参（`uid` / `bizNo` / `amount` / `fundFlowType`）为 null 或 `uid <= 0`
- **THEN** 系统必须抛 `BizException`，业务错误码必须为对应的参数校验错误

### Requirement: 提现扣减必须只动冻结余额

系统必须（MUST）通过 `AccountEntity#withdraw(amount)` 实现提现扣减：仅扣减 `frozenBalance`，不得扣减 `availableBalance`。`amount` 必须为正数；当 `frozenBalance < amount`（即冻结余额严格小于提现金额）时必须抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`；`frozenBalance == amount` 为合法边界，允许全额结算。该方法不得校验 `availableBalance`（结算与可用余额无关）。

#### Scenario: 正常提现扣减

- **WHEN** 调用 `withdraw(amount)`，`amount > 0` 且 `amount <= frozenBalance`
- **THEN** 账户的 `frozenBalance` 必须减少 `amount`，`availableBalance` 保持不变

#### Scenario: 提现金额恰好等于冻结余额

- **WHEN** 调用 `withdraw(amount)` 且 `amount == frozenBalance`
- **THEN** 系统必须成功，`frozenBalance` 变为 0，`availableBalance` 保持不变

#### Scenario: 可用余额为零时仍可扣减

- **WHEN** 调用 `withdraw(amount)`，`availableBalance = 0` 且 `amount <= frozenBalance`
- **THEN** 系统必须成功（不得抛 `INSUFFICIENT_BALANCE`），仅扣减 `frozenBalance`

#### Scenario: 提现金额超过冻结余额

- **WHEN** 调用 `withdraw(amount)` 且 `amount > frozenBalance`
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_FROZEN_BALANCE`（不得抛 `IllegalArgumentException`）

#### Scenario: 提现金额非法

- **WHEN** 调用 `withdraw(amount)` 且 `amount` 为 null 或 `<= 0`
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `WITHDRAW_AMOUNT_INVALID`
