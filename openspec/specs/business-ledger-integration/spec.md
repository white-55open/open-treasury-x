# business-ledger-integration 规范

## 目的

业务-总账集成（business-ledger-integration）上下文定义充值（Deposit）与提现（Withdraw）业务流程自动过账到总账（Ledger）的集成行为：每笔资金变动在完成账户余额变更与资金流水记录后，必须生成一张对应的复式记账凭证。它保证业务流水与会计凭证一一对应，支撑财务报表与审计追溯。

集成边界：账户余额变更属于 account 上下文，资金流水记录属于 fundflow 上下文，复式记账凭证属于 ledger 上下文；本上下文仅定义三者之间的编排规则（分录映射、幂等键、事务边界），不新增聚合根。

## 需求

### 需求:充值业务必须自动过账到总账

充值业务流程（`DepositAppServiceImpl.deposit()`）在完成账户余额变更和资金流水记录后，必须自动调用 `LedgerAppService.postJournal()` 生成总账凭证，确保每笔充值在总账中产生对应的复式记账分录。

- 过账必须使用与资金流水相同的 `bizNo` 作为幂等键
- 充值过账的业务类型必须为 `DEPOSIT_ONCHAIN`
- 过账分录必须且仅包含两条：`DEBIT DEPOSIT_IN_TRANSIT` 和 `CREDIT USER_AVAILABLE`
- 过账失败时禁止回滚余额变更和资金流水（最终一致性）
- `currency` 从 `ChangeAmountRequest.currency` 获取，为空时过账抛 `LEDGER_CURRENCY_EMPTY`，余额变更不受影响

#### 场景:充值成功且过账成功
- **当** 用户发起一笔充值请求，币种为 USDT，金额为 100
- **那么** 系统必须完成：用户余额增加 100 → 资金流水记录（IN/DEPOSIT）→ 总账过账（DEBIT DEPOSIT_IN_TRANSIT / CREDIT USER_AVAILABLE 各 100）
- **那么** 总账模块中必须存在一条 bizNo 匹配的凭证，状态为 POSTED

#### 场景:充值成功但过账失败
- **当** 用户发起一笔充值，余额变更和流水记录成功，但总账过账抛出异常
- **那么** 系统必须返回成功（余额已变更）
- **那么** 日志必须记录 WARN 级别的过账失败信息
- **那么** 总账模块中不包含该 bizNo 的凭证

#### 场景:充值幂等—同一 bizNo 第二次请求
- **当** 用户使用相同的 bizNo 重复发起充值请求
- **那么** 余额变更和流水记录走幂等返回
- **那么** 总账过账走幂等返回已存在的凭证
- **那么** 总账中仅有一条该 bizNo 的凭证

#### 场景:充值过账分录校验
- **当** 一笔充值金额为 100 USDT 成功过账
- **那么** 凭证必须包含 2 条分录
- **那么** DEBIT 分录的科目必须为 `DEPOSIT_IN_TRANSIT`，金额为 100，uid 为 null
- **那么** CREDIT 分录的科目必须为 `USER_AVAILABLE`，金额为 100，uid 为充值用户
- **那么** 借方总额必须等于贷方总额

### 需求:提现业务必须自动过账到总账

提现业务流程（`WithdrawAppServiceImpl.withdraw()`）在完成账户余额变更和资金流水记录后，必须自动调用 `LedgerAppService.postJournal()` 生成总账凭证，确保每笔提现在总账中产生对应的复式记账分录。

- 过账必须使用与资金流水相同的 `bizNo` 作为幂等键
- 提现过账的业务类型必须为 `WITHDRAW_ONCHAIN`
- 过账分录必须且仅包含两条：`DEBIT USER_AVAILABLE` 和 `CREDIT WITHDRAW_IN_TRANSIT`
- 过账失败时禁止回滚余额变更和资金流水（最终一致性）
- `currency` 从 `ChangeAmountRequest.currency` 获取，为空时过账抛 `LEDGER_CURRENCY_EMPTY`，余额变更不受影响

#### 场景:提现成功且过账成功
- **当** 用户发起一笔提现请求，金额为 50
- **那么** 系统必须完成：用户余额减少 50 → 资金流水记录（OUT/WITHDRAW）→ 总账过账（DEBIT USER_AVAILABLE / CREDIT WITHDRAW_IN_TRANSIT 各 50）
- **那么** 总账模块中必须存在一条 bizNo 匹配的凭证，状态为 POSTED

#### 场景:提现成功但过账失败
- **当** 用户发起一笔提现，余额变更和流水记录成功，但总账过账抛出异常
- **那么** 系统必须返回成功（余额已变更）
- **那么** 日志必须记录 WARN 级别的过账失败信息
- **那么** 总账模块中不包含该 bizNo 的凭证

#### 场景:提现幂等—同一 bizNo 第二次请求
- **当** 用户使用相同的 bizNo 重复发起提现请求
- **那么** 余额变更和流水记录走幂等返回
- **那么** 总账过账走幂等返回已存在的凭证
- **那么** 总账中仅有一条该 bizNo 的凭证

#### 场景:提现过账分录校验
- **当** 一笔提现金额为 50 成功过账
- **那么** 凭证必须包含 2 条分录
- **那么** DEBIT 分录的科目必须为 `USER_AVAILABLE`，金额为 50，uid 为提现用户
- **那么** CREDIT 分录的科目必须为 `WITHDRAW_IN_TRANSIT`，金额为 50，uid 为 null
- **那么** 借方总额必须等于贷方总额

### 需求:ChangeAmountRequest 必须携带币种信息

`ChangeAmountRequest` DTO 必须新增 `currency` 字段，用于在过账时填充凭证币种。

- 字段类型为 `String`
- 不要求必填（后端不做非空校验）

#### 场景:请求携带 currency
- **当** 充值请求的 `currency` 为 "USDT"
- **那么** 总账凭证的币种必须为 "USDT"

#### 场景:请求未携带 currency
- **当** 充值请求未设置 `currency` 字段
- **那么** 过账时 currency 为 null，`LedgerJournalEntity.create()` 将抛 `LEDGER_CURRENCY_EMPTY` 异常
- **那么** 余额变更不受影响，日志记录过账失败
