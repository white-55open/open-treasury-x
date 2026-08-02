## ADDED Requirements

### Requirement: Journal 支持以 DRAFT 状态持久化创建

应用服务必须（MUST）（MUST）提供 `createDraftJournal(PostJournalRequest)` 用例：创建并持久化一张**状态为 DRAFT** 的 Journal（含链上字段 chainId/chainTxHash/tokenAddress），**不得**调用 post() 过账。用于链上待确认业务（如提现广播后、链上确认前）的凭证留档。事务边界必须（MUST）与既有 postJournalAtomic 一致（REQUIRES_NEW + 乐观锁重试），幂等语义与既有 postJournal 一致（相同 bizNo 只产生一张 Journal，重复请求返回已存在凭证）。

#### Scenario: 正常创建 DRAFT 凭证
- **WHEN** 客户端调用 createDraftJournal 提交含 chainId、chainTxHash 的过账请求
- **THEN** 系统必须持久化一张状态为 DRAFT 的 Journal，chainTxHash 与请求一致
- **THEN** 系统必须返回该 Journal 的完整详情，status 为 DRAFT

#### Scenario: 相同 bizNo 重复创建返回已有 DRAFT 凭证
- **WHEN** 客户端先后两次以相同 bizNo 调用 createDraftJournal
- **THEN** 两次请求必须返回同一张 Journal，数据库中仅有一条该 bizNo 的 DRAFT 凭证

#### Scenario: 创建 DRAFT 凭证同样校验借贷平衡
- **WHEN** 客户端提交借贷不平衡的分录调用 createDraftJournal
- **THEN** 系统必须抛出 BizException，错误码为 LEDGER_NOT_BALANCED，且不持久化任何数据

### Requirement: Journal 支持按业务号过账

应用服务必须（MUST）（MUST）提供 `postJournalByBizNo(String bizNo)` 用例：按业务号将 DRAFT 状态的 Journal 过账为 POSTED，状态变更必须（MUST）通过聚合根 post() 领域方法完成。用例必须（MUST）幂等：凭证已 POSTED 时直接返回现有凭证，不产生副作用。

#### Scenario: DRAFT 凭证过账为 POSTED
- **WHEN** 客户端调用 postJournalByBizNo 且该 bizNo 存在 DRAFT 凭证
- **THEN** 系统必须将凭证状态置为 POSTED 并返回更新后的凭证详情

#### Scenario: 已 POSTED 凭证重复过账返回现有凭证
- **WHEN** 客户端对已 POSTED 的凭证再次调用 postJournalByBizNo
- **THEN** 系统必须直接返回该凭证，状态保持 POSTED，不产生副作用

#### Scenario: 不存在的 bizNo 过账报错
- **WHEN** 客户端调用 postJournalByBizNo 且该 bizNo 无对应凭证
- **THEN** 系统必须抛出 BizException，错误码为 LEDGER_JOURNAL_NOT_FOUND

#### Scenario: REVERSED 凭证不可过账
- **WHEN** 客户端对状态为 REVERSED 的凭证调用 postJournalByBizNo
- **THEN** 系统必须抛出 BizException，错误码为 LEDGER_JOURNAL_NOT_DRAFT

### Requirement: 既有 postJournal 用例行为保持不变

`postJournal(PostJournalRequest)` 既有用例（创建并立即过账为 POSTED）的行为必须（MUST）（MUST）保持不变，不得因新增 DRAFT 创建与按业务号过账用例而改变。

#### Scenario: 既有过账请求仍创建 POSTED 凭证
- **WHEN** 客户端调用 postJournal 提交借贷平衡的过账请求
- **THEN** 系统必须创建状态为 POSTED 的 Journal（与变更前行为一致）

#### Scenario: 既有幂等行为不变
- **WHEN** 客户端以相同 bizNo 重复调用 postJournal
- **THEN** 两次请求必须返回同一张 POSTED 凭证，数据库中仅有一条该 bizNo 的 Journal
