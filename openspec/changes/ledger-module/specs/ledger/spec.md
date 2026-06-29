# Ledger 总账规范

## 新增需求

### 需求:总账采用复式记账

系统必须对每笔业务事件采用复式记账：每张凭证（Journal）必须包含至少一条 DEBIT 分录和至少一条 CREDIT 分录，且所有 DEBIT 金额之和必须等于所有 CREDIT 金额之和（借贷必平）。

#### 场景:借贷平衡的凭证过账成功
- **当** 应用服务收到 entries 包含一条 DEBIT 100 与一条 CREDIT 100 的过账请求
- **那么** 系统必须创建一张状态为 POSTED 的 Journal，并持久化全部 Entry

#### 场景:借贷不平衡被拒绝
- **当** 应用服务收到 entries 包含一条 DEBIT 100 与一条 CREDIT 99 的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_NOT_BALANCED，且不持久化任何数据

#### 场景:缺少 DEBIT 分录被拒绝
- **当** 应用服务收到 entries 只包含 CREDIT 分录的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_ENTRIES_EMPTY 或 LEDGER_NOT_BALANCED

#### 场景:缺少 CREDIT 分录被拒绝
- **当** 应用服务收到 entries 只包含 DEBIT 分录的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_ENTRIES_EMPTY 或 LEDGER_NOT_BALANCED

#### 场景:空 entries 被拒绝
- **当** 应用服务收到 entries 为空数组的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_ENTRIES_EMPTY

### 需求:同一账户在同一方向上不可重复

系统必须保证在一张 Journal 内，同一个 accountCode 在同一 entryType 下最多出现一次。

#### 场景:同账户同方向重复被拒绝
- **当** 应用服务收到 entries 包含两条 PLATFORM_HOT 的 DEBIT 分录
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_DUPLICATE_ACCOUNT

#### 场景:同账户借贷分录允许
- **当** 应用服务收到 entries 包含一条 PLATFORM_HOT 的 DEBIT 和一条 PLATFORM_HOT 的 CREDIT
- **那么** 系统必须成功过账

### 需求:Entry 为不可变值对象

系统必须将 Entry 设计为不可变值对象：金额为正数、构造时自验证、构造后字段不可修改。状态变更只能通过生成新的反向 Journal 实现。

#### 场景:Entry 金额必须为正
- **当** 构造 Entry 时传入 amount ≤ 0
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_AMOUNT_INVALID

#### 场景:Entry 账户编码必须合法
- **当** 构造 Entry 时传入 accountCode 不在 LedgerAccountCodeEnum 枚举集合内
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_ACCOUNT_CODE_INVALID

#### 场景:Entry 方向必须为 DEBIT 或 CREDIT
- **当** 构造 Entry 时传入 entryType 不在 DEBIT/CREDIT 集合内
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_ENTRY_TYPE_INVALID

### 需求:Journal 状态机

系统必须实现 Journal 状态机：DRAFT（创建后未过账）、POSTED（已过账）、REVERSED（已冲销）。状态转换必须通过领域方法 post() / reverse() 完成，禁止外部直接修改状态。

#### 场景:DRAFT 可过账为 POSTED
- **当** 一张状态为 DRAFT 的 Journal 调用 post()
- **那么** 状态必须变为 POSTED

#### 场景:POSTED 不可再次过账
- **当** 一张状态为 POSTED 的 Journal 调用 post()
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_JOURNAL_NOT_DRAFT

#### 场景:POSTED 可冲销为 REVERSED
- **当** 一张状态为 POSTED 的 Journal 调用 reverse(reasonJournal)
- **那么** 系统必须创建一张新的反向 Journal（所有 Entry 借贷方向互换），并将原 Journal 状态置为 REVERSED

#### 场景:REVERSED 不可冲销
- **当** 一张状态为 REVERSED 的 Journal 调用 reverse()
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_JOURNAL_NOT_POSTED

### 需求:过账具备业务幂等性

系统必须保证对相同 bizNo 的过账请求只产生一张 Journal。重复请求必须返回已存在的 Journal，且不产生副作用。

#### 场景:相同 bizNo 重复过账返回已有 Journal
- **当** 应用服务先后收到两个 bizNo 相同的过账请求
- **那么** 两次请求必须返回同一张 JournalDetailResponse，且数据库中只有一条 bizNo 对应的 Journal

#### 场景:Entry 联合唯一索引防止分录重复
- **当** 系统尝试插入 (biz_no, account_code, entry_type) 重复的 Entry
- **那么** 数据库必须抛出 DuplicateKeyException，并由应用层转换为 BizIdempotentException

#### 场景:Journal 主键 bizNo 唯一
- **当** 系统尝试插入 bizNo 重复的 Journal
- **那么** 数据库必须抛出 DuplicateKeyException，并由应用层转换为 BizIdempotentException

### 需求:Web3 链上字段预埋

Journal 主表必须包含以下 Web3 链上追溯字段，允许为空：chain_id、chain_tx_hash、block_number、token_address、confirmations。这些字段本期不强制填写，但必须支持存储与查询。

#### 场景:链上充值的 Journal 携带链上字段
- **当** 应用服务收到过账请求且请求体包含 chainId、chainTxHash、blockNumber、tokenAddress
- **那么** 持久化的 Journal 必须包含以上字段值

#### 场景:内部转账的 Journal 链上字段为空
- **当** 应用服务收到过账请求且请求体不包含任何链上字段
- **那么** 持久化的 Journal 的链上字段必须为 NULL

#### 场景:按链 txHash 查询
- **当** 用户请求查询某 chainTxHash 对应的 Journal
- **那么** 系统必须支持通过 chain_tx_hash 索引定位 Journal（链下业务返回空）

### 需求:ChainQueryPort 端口预留给 web3j

系统必须在 domain 层定义 ChainQueryPort 出站端口，提供链上交易回执、当前区块高度、确认数判断三个方法。本期不提供实现。

#### 场景:ChainQueryPort 定义 queryTxReceipt
- **当** 应用代码调用 chainQueryPort.queryTxReceipt(chainId, txHash)
- **那么** 接口必须返回 Optional<ChainTxReceipt>，空 Optional 表示链上查不到

#### 场景:ChainQueryPort 定义 currentBlockNumber
- **当** 应用代码调用 chainQueryPort.currentBlockNumber(chainId)
- **那么** 接口必须返回 Long 类型的当前区块高度

#### 场景:ChainQueryPort 定义 isConfirmed
- **当** 应用代码调用 chainQueryPort.isConfirmed(chainId, txHash, requiredConfirmations)
- **那么** 接口必须返回 boolean，true 表示已确认数 ≥ requiredConfirmations

### 需求:postJournal 用例

应用服务必须提供 postJournal(PostJournalRequest) 用例，负责创建并过账 Journal。事务边界必须在 postJournalAtomic 内部开启 REQUIRES_NEW，外层入口做幂等检查。

#### 场景:正常过账的请求与响应字段
- **当** 客户端 POST /ledger/journals 提交过账请求
- **那么** 系统必须返回 HTTP 200 与 Result<JournalDetailResponse>，包含 journal 完整字段（id、bizNo、bizType、currency、status、totalAmount、entries 列表）

#### 场景:乐观锁冲突自动重试
- **当** postJournalAtomic 内部因并发导致 OptimisticLockException
- **那么** 系统必须按照 maxAttempts=5、指数退避策略自动重试

#### 场景:DuplicateKeyException 转换为 BizIdempotentException
- **当** postJournalAtomic 内部因重复键触发 DuplicateKeyException
- **那么** 系统必须抛出 BizIdempotentException，且外层 postJournal 入口必须捕获并视为幂等成功

### 需求:findByBizNo 用例

应用服务必须提供 findByBizNo(String bizNo) 用例，按业务号查询 Journal 及其全部 Entry。

#### 场景:按 bizNo 命中
- **当** 客户端 GET /ledger/journals/{bizNo} 且 bizNo 存在
- **那么** 系统必须返回 HTTP 200 与 Result<JournalDetailResponse>

#### 场景:按 bizNo 未命中
- **当** 客户端 GET /ledger/journals/{bizNo} 且 bizNo 不存在
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_JOURNAL_NOT_FOUND

### 需求:必填字段校验

系统必须对过账请求做以下必填校验：bizNo 非空、currency 非空、bizType 在枚举集合内、postingDate 合法。

#### 场景:bizNo 缺失被拒绝
- **当** 客户端提交 bizNo 为 null 或空字符串的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_BIZ_NO_EMPTY

#### 场景:currency 缺失被拒绝
- **当** 客户端提交 currency 为 null 或空字符串的过账请求
- **那么** 系统必须抛出 BizException，错误码为 LEDGER_CURRENCY_EMPTY

### 需求:既有 account_t 与 fund_flow_t 不受影响

本期变更必须不修改 AccountAppService、FundFlowAppService 的既有行为，account_t 与 fund_flow_t 既有数据保持兼容。account_t 仅追加 account_code 列（默认 USER_AVAILABLE）。

#### 场景:account_t 历史数据兼容
- **当** 数据库执行 V4__account_accountcode.sql
- **那么** account_t 历史行的 account_code 必须被填充为 USER_AVAILABLE，且不破坏既有字段

#### 场景:AccountAppService 既有 API 不变
- **当** 客户端调用 AccountAppService 的 createAccount / increaseBalance / freezeBalance / getByUid
- **那么** 系统行为必须与变更前一致

#### 场景:FundFlowAppService 既有 API 不变
- **当** 客户端调用 FundFlowAppService 的 record / existsBizNo / findByUid
- **那么** 系统行为必须与变更前一致

### 需求:账户主数据本期不实现

系统本期必须不实现 ledger_account_t 表与 LedgerAccount 实体。账户编码合法性校验仅依据 LedgerAccountCodeEnum 枚举的 hardcode 值。

#### 场景:账户编码合法性仅校验枚举
- **当** 构造 Entry 时传入非 LedgerAccountCodeEnum 枚举值的 accountCode
- **那么** 系统必须抛出 LEDGER_ACCOUNT_CODE_INVALID，且不查询任何数据库主数据

#### 场景:不存在 ledger_account_t 表
- **当** 任何模块尝试访问 ledger_account_t 表
- **那么** 系统必须因表不存在而失败（本期不创建该表）
