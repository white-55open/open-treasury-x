## ADDED Requirements

### Requirement: 提现必须为冻结-结算两阶段流程

系统必须（MUST）将提现拆分为三个独立用例：**冻结（freeze）**、**结算（withdraw）**、**解冻（unfreeze）**，由 `WithdrawAppService` 提供。每个用例必须携带独立业务号 `bizNo` 作为幂等键，且结算与解冻必须以冻结为前提——结算从冻结余额扣减，解冻把冻结余额退回可用余额。

- 冻结：申请提现时锁定资金（可用 → 冻结）
- 结算：审批通过后从冻结余额扣减（资金离开账户体系，进入提现在途）
- 解冻：取消/驳回时释放资金（冻结 → 可用）
- 三个用例的调用顺序不得被系统强制校验（阶段机不在本变更范围），但每个用例的资金与账务效果必须与上述定义一致

#### Scenario: 完整两阶段提现成功

- **WHEN** 用户账户可用余额为 100、冻结余额为 0，先以 bizNo=F1 调用冻结 50，再以 bizNo=S1 调用结算 50
- **THEN** 最终账户可用余额必须为 50、冻结余额必须为 0
- **THEN** 系统中必须存在 F1 对应的 FREEZE 流水与 FREEZE 凭证、S1 对应的 WITHDRAW 流水与 WITHDRAW_ONCHAIN 凭证，且各一条

#### Scenario: 冻结后取消提现（解冻归还）

- **WHEN** 用户账户可用余额为 100，先以 bizNo=F1 调用冻结 50，再以 bizNo=U1 调用解冻 50
- **THEN** 最终账户可用余额必须为 100、冻结余额必须为 0
- **THEN** 系统中必须存在 F1 的 FREEZE 流水与凭证、U1 的 UNFREEZE 流水与凭证，冻结凭证状态保持 POSTED

### Requirement: 冻结阶段必须锁定资金并记录流水与凭证

系统必须（MUST）通过 `WithdrawAppService.freeze(request)` 完成冻结。冻结必须满足：

- 入参校验：`uid` / `bizNo` / `amount` / `currency` 非空；`amount > 0`，否则抛 `BizException(FREEZE_AMOUNT_INVALID)`
- 资金约束：`availableBalance >= amount`，否则抛 `BizException(INSUFFICIENT_BALANCE)`，账户余额不得变化
- 资金效果：`availableBalance` 减少 amount，`frozenBalance` 增加 amount，双余额之和守恒
- 流水效果：fundflow 必须落一条 `direction = OUT`、`type = FREEZE` 的流水，`balanceBefore` / `balanceAfter` 分别为冻结前后可用余额快照
- 账务效果：必须过账一张 `bizType = FREEZE` 的凭证，包含且仅包含两条分录：`DEBIT USER_AVAILABLE`（uid = 冻结用户）与 `CREDIT USER_FROZEN`（uid = 冻结用户），金额各为 amount
- 过账失败时禁止回滚余额与流水（最终一致性，靠对账修复），日志记录 WARN 级过账失败

#### Scenario: 冻结成功

- **WHEN** 调用 `freeze` 且账户可用余额 100、冻结余额 0、金额 50
- **THEN** 可用余额必须为 50、冻结余额必须为 50
- **THEN** fundflow 必须新增一条 FREEZE 流水，`direction = OUT`、`balanceBefore = 100`、`balanceAfter = 50`
- **THEN** 总账必须新增一张 FREEZE 凭证：DEBIT USER_AVAILABLE 50 / CREDIT USER_FROZEN 50

#### Scenario: 冻结金额超过可用余额被拒绝

- **WHEN** 调用 `freeze` 且账户可用余额 50、冻结金额 60
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_BALANCE`
- **THEN** 账户可用余额与冻结余额均不得变化，fundflow 与总账不得新增任何记录

#### Scenario: 冻结金额非法被拒绝

- **WHEN** 调用 `freeze` 且金额为 null 或 <= 0
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `FREEZE_AMOUNT_INVALID`

#### Scenario: 冻结过账失败不影响资金

- **WHEN** 调用 `freeze`，余额变更与流水记录成功，但总账过账抛出异常
- **THEN** 系统必须返回成功（资金已冻结），日志记录 WARN 级过账失败
- **THEN** 总账中不得存在该 bizNo 的凭证

#### Scenario: 冻结凭证分录校验

- **WHEN** 一笔冻结金额为 50 成功完成
- **THEN** 凭证必须包含 2 条分录
- **THEN** DEBIT 分录科目必须为 `USER_AVAILABLE`、金额 50、uid 为冻结用户
- **THEN** CREDIT 分录科目必须为 `USER_FROZEN`、金额 50、uid 为冻结用户
- **THEN** 借方总额必须等于贷方总额

### Requirement: 结算阶段必须从冻结余额扣减并记录流水与凭证

系统必须（MUST）通过 `WithdrawAppService.withdraw(request)` 完成结算。结算必须满足：

- 入参校验：`uid` / `bizNo` / `amount` / `currency` 非空；`amount > 0`，否则抛 `BizException(WITHDRAW_AMOUNT_INVALID)`
- 资金约束：只校验 `frozenBalance >= amount`，不校验 `availableBalance`；冻结不足时抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`，账户余额不得变化
- 边界语义：`frozenBalance == amount` 为合法边界，允许全额结算
- 资金效果：`frozenBalance` 减少 amount，`availableBalance` 不得变化
- 流水效果：fundflow 必须落一条 `direction = OUT`、`type = WITHDRAW` 的流水，`balanceBefore` / `balanceAfter` 分别为结算前后冻结余额快照
- 账务效果：必须过账一张 `bizType = WITHDRAW_ONCHAIN` 的凭证，包含且仅包含两条分录：`DEBIT USER_FROZEN`（uid = 提现用户）与 `CREDIT WITHDRAW_IN_TRANSIT`（uid = null），金额各为 amount
- 过账失败时禁止回滚余额与流水（最终一致性，靠对账修复），日志记录 WARN 级过账失败

#### Scenario: 结算成功（从冻结余额扣减）

- **WHEN** 账户可用余额 50、冻结余额 50，调用结算金额 50
- **THEN** 冻结余额必须为 0，可用余额必须保持 50 不变
- **THEN** fundflow 必须新增一条 WITHDRAW 流水，`direction = OUT`、`balanceBefore = 50`、`balanceAfter = 0`
- **THEN** 总账必须新增一张 WITHDRAW_ONCHAIN 凭证：DEBIT USER_FROZEN 50 / CREDIT WITHDRAW_IN_TRANSIT 50

#### Scenario: 结算时可用余额不足仍可成功

- **WHEN** 账户可用余额 0、冻结余额 50，调用结算金额 50
- **THEN** 系统必须成功，冻结余额变为 0，可用余额保持 0
- **THEN** 不得抛 `INSUFFICIENT_BALANCE`（结算只校验冻结余额）

#### Scenario: 结算金额恰好等于冻结余额

- **WHEN** 账户冻结余额恰好为 50，调用结算金额 50（`frozenBalance == amount`）
- **THEN** 系统必须成功，冻结余额变为 0

#### Scenario: 结算冻结余额不足被拒绝

- **WHEN** 账户冻结余额 30，调用结算金额 50
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_FROZEN_BALANCE`
- **THEN** 账户余额不得变化，fundflow 与总账不得新增任何记录

#### Scenario: 结算金额非法被拒绝

- **WHEN** 调用结算且金额为 null 或 <= 0
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `WITHDRAW_AMOUNT_INVALID`

#### Scenario: 结算过账失败不影响资金

- **WHEN** 调用结算，冻结扣减与流水记录成功，但总账过账抛出异常
- **THEN** 系统必须返回成功（资金已扣减），日志记录 WARN 级过账失败
- **THEN** 总账中不得存在该 bizNo 的凭证

#### Scenario: 结算凭证分录校验

- **WHEN** 一笔结算金额为 50 成功完成
- **THEN** 凭证必须包含 2 条分录
- **THEN** DEBIT 分录科目必须为 `USER_FROZEN`、金额 50、uid 为提现用户
- **THEN** CREDIT 分录科目必须为 `WITHDRAW_IN_TRANSIT`、金额 50、uid 为 null
- **THEN** 借方总额必须等于贷方总额

### Requirement: 解冻阶段必须释放资金并记录流水与凭证

系统必须（MUST）通过 `WithdrawAppService.unfreeze(request)` 完成解冻。解冻必须满足：

- 入参校验：`uid` / `bizNo` / `amount` / `currency` 非空；`amount > 0`，否则抛 `BizException(UNFREEZE_AMOUNT_INVALID)`
- 资金约束：`frozenBalance >= amount`，否则抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`，账户余额不得变化
- 资金效果：`frozenBalance` 减少 amount，`availableBalance` 增加 amount，双余额之和守恒
- 流水效果：fundflow 必须落一条 `direction = IN`、`type = UNFREEZE` 的流水，`balanceBefore` / `balanceAfter` 分别为解冻前后可用余额快照
- 账务效果：必须过账一张 `bizType = UNFREEZE` 的凭证，包含且仅包含两条分录：`DEBIT USER_FROZEN`（uid = 解冻用户）与 `CREDIT USER_AVAILABLE`（uid = 解冻用户），金额各为 amount。**决策**：解冻发布独立凭证，不冲销原冻结凭证（原冻结凭证保持 POSTED 状态）
- 过账失败时禁止回滚余额与流水（最终一致性，靠对账修复），日志记录 WARN 级过账失败

#### Scenario: 解冻成功

- **WHEN** 账户可用余额 0、冻结余额 50，调用解冻金额 50
- **THEN** 可用余额必须为 50、冻结余额必须为 0
- **THEN** fundflow 必须新增一条 UNFREEZE 流水，`direction = IN`、`balanceBefore = 0`、`balanceAfter = 50`
- **THEN** 总账必须新增一张 UNFREEZE 凭证：DEBIT USER_FROZEN 50 / CREDIT USER_AVAILABLE 50

#### Scenario: 解冻冻结余额不足被拒绝

- **WHEN** 账户冻结余额 30，调用解冻金额 50
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_FROZEN_BALANCE`
- **THEN** 账户余额不得变化，fundflow 与总账不得新增任何记录

#### Scenario: 解冻金额非法被拒绝

- **WHEN** 调用解冻且金额为 null 或 <= 0
- **THEN** 系统必须抛 `BizException`，业务错误码必须为 `UNFREEZE_AMOUNT_INVALID`

#### Scenario: 解冻凭证分录校验

- **WHEN** 一笔解冻金额为 50 成功完成
- **THEN** 凭证必须包含 2 条分录
- **THEN** DEBIT 分录科目必须为 `USER_FROZEN`、金额 50、uid 为解冻用户
- **THEN** CREDIT 分录科目必须为 `USER_AVAILABLE`、金额 50、uid 为解冻用户
- **THEN** 借方总额必须等于贷方总额

### Requirement: 提现各阶段必须幂等

系统必须（MUST）保证冻结、结算、解冻三个用例各自独立幂等：对相同 `bizNo` 的重复请求不得重复动账、不得重复落流水、不得重复过账，必须返回与首次调用一致的结果。幂等必须三层保障：入口 `existsBizNo` 前置检查 → `fund_flow_t.biz_no` 唯一索引（`DuplicateKeyException` 转换为 `BizIdempotentException`）→ 入口捕获后视为幂等成功；总账过账沿用 `postJournal` 自身幂等（相同 bizNo 返回已存在凭证）。

#### Scenario: 冻结幂等—同一 bizNo 重复调用

- **WHEN** 使用相同 bizNo 连续两次调用冻结
- **THEN** 第二次调用必须返回相同结果，账户余额不得再次变化
- **THEN** 该 bizNo 的 FREEZE 流水与凭证在库中必须各只有一条

#### Scenario: 结算幂等—同一 bizNo 重复调用

- **WHEN** 使用相同 bizNo 连续两次调用结算
- **THEN** 第二次调用必须返回相同结果，冻结余额不得再次扣减
- **THEN** 该 bizNo 的 WITHDRAW 流水与凭证在库中必须各只有一条

#### Scenario: 解冻幂等—同一 bizNo 重复调用

- **WHEN** 使用相同 bizNo 连续两次调用解冻
- **THEN** 第二次调用必须返回相同结果，余额不得再次变化
- **THEN** 该 bizNo 的 UNFREEZE 流水与凭证在库中必须各只有一条

#### Scenario: 并发重复提交由唯一索引兜底

- **WHEN** 两个并发请求携带相同 bizNo 同时进入某阶段原子方法
- **THEN** 后插入的一方必须触发 `DuplicateKeyException` 并转换为 `BizIdempotentException`，入口视为幂等成功
- **THEN** 最终该 bizNo 的流水与凭证各只有一条
