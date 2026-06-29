# 任务清单：Ledger 模块

颗粒度规则：每个 task 单一目标、可在 1-2 小时内完成、对应一次 commit。
测试要求：domain 层 task 必须配单元测试；infra/application task 通过 mvn test 验证；接口层 task 通过接口层单测验证。

## 1. 域枚举（Domain Enums）

- [ ] 1.1 创建 4 个枚举类（LedgerJournalStatusEnum / LedgerEntryTypeEnum / LedgerBizTypeEnum / LedgerAccountCodeEnum），通过 `mvn -pl otx-domain compile`
  - 状态：DRAFT / POSTED / REVERSED
  - Entry 方向：DEBIT / CREDIT
  - 业务类型：DEPOSIT_ONCHAIN / WITHDRAW_ONCHAIN / INTERNAL_TRANSFER / FEE / REVERSAL / ADJUSTMENT
  - 系统账户编码：USER_AVAILABLE / USER_FROZEN / PLATFORM_HOT / PLATFORM_COLD / DEPOSIT_IN_TRANSIT / WITHDRAW_IN_TRANSIT / GAS_PAYABLE / DEPOSIT_FEE_REVENUE / GAS_EXPENSE / WITHDRAW_FEE_EXPENSE

## 2. 域实体（Domain Entities）

- [ ] 2.1 创建 LedgerEntryEntity（不可变值对象），删除旧半成品 `otx-domain/.../ledger/LedgerEntryEntity.java`，通过 `mvn -pl otx-domain compile`
  - 字段全部 final：accountCode / entryType / amount / uid / counterparty / balanceAfter / remark
  - 构造时 self-validate：amount > 0、accountCode 在枚举内、entryType 在 DEBIT/CREDIT 内
  - 单元测试覆盖：amount≤0 抛 LEDGER_AMOUNT_INVALID、accountCode 非法抛 LEDGER_ACCOUNT_CODE_INVALID、entryType 非法抛 LEDGER_ENTRY_TYPE_INVALID

- [ ] 2.2 创建 LedgerJournalEntity 骨架（字段 + create 工厂），通过 `mvn -pl otx-domain compile`
  - 字段：id / bizNo / bizType / postingDate / currency / status / totalAmount / description / chainId / chainTxHash / blockNumber / tokenAddress / confirmations / reversedBy
  - 工厂方法 `create(bizNo, bizType, currency, entries, postingDate, ...)` 做入参校验
  - 持有 `List<LedgerEntry> entries`（不可变 wrapper）
  - 单元测试覆盖：create 入参校验（bizNo 非空、currency 非空）

- [ ] 2.3 实现 Journal 不变量（assertBalanced + assertNoDuplicateAccount），通过 `mvn -pl otx-domain test`
  - `assertBalanced()`：sum(DEBIT) == sum(CREDIT)、DEBIT 至少一条、CREDIT 至少一条、entries 非空
  - `assertNoDuplicateAccount()`：同 accountCode 同 entryType 不重复
  - 单元测试覆盖：金额不等抛 LEDGER_NOT_BALANCED、缺 DEBIT/CREDIT 抛 LEDGER_ENTRIES_EMPTY、同户同向重复抛 LEDGER_DUPLICATE_ACCOUNT

- [ ] 2.4 实现 Journal.post() 状态转换 DRAFT → POSTED，通过 `mvn -pl otx-domain test`
  - 仅 DRAFT 状态可 post；POSTED 重复 post 抛 LEDGER_JOURNAL_NOT_DRAFT
  - post() 后 status 字段更新
  - 单元测试覆盖：DRAFT 可 post、POSTED 重复 post 抛异常

- [ ] 2.5 实现 Journal.reverse(reason) 产生反向 Journal，通过 `mvn -pl otx-domain test`
  - 仅 POSTED 状态可 reverse；REVERSED 重复 reverse 抛 LEDGER_JOURNAL_NOT_POSTED
  - reverse 产生新 Journal（所有 entry 的 entryType 取反、bizNo 以 "RV-" 前缀），原 Journal 状态置 REVERSED，记录 reversedBy
  - 单元测试覆盖：POSTED 可 reverse、REVERSED 重复 reverse 抛异常、反向 Journal 借贷方向互换

## 3. 域服务与端口（Domain Service & Ports）

- [ ] 3.1 创建 LedgerPostingService 领域服务（无状态编排），通过 `mvn -pl otx-domain test`
  - 方法 `postJournal(LedgerJournalEntity)` 编排：assertBalanced → assertNoDuplicateAccount
  - 复用 domain 行为，不引入新业务规则
  - 单元测试覆盖：服务正常编排、不变量违反时抛异常

- [ ] 3.2 创建 2 个仓储接口（LedgerJournalRepo / LedgerEntryRepo），通过 `mvn -pl otx-domain compile`
  - LedgerJournalRepo：save / findByBizNo / existsByBizNo / update
  - LedgerEntryRepo：saveBatch / findByJournalId / findByBizNo
  - 命名体现集合语义（save / find / exists）

- [ ] 3.3 创建 ChainQueryPort 出站端口（Web3 预留），通过 `mvn -pl otx-domain compile`
  - 方法签名：`Optional<ChainTxReceipt> queryTxReceipt(String chainId, String txHash)`、`Long currentBlockNumber(String chainId)`、`boolean isConfirmed(String chainId, String txHash, int requiredConfirmations)`
  - 创建 ChainTxReceipt 值对象（chainId / txHash / blockNumber / status / confirmations / from / to / value）
  - 本期不写实现类

## 4. SQL 迁移

- [ ] 4.1 改造 V2__ledger.sql 为 ledger_journal_t 主表
  - 重写 docs/sql/V2__ledger.sql：原 ledger_entry_t 拆分为 ledger_journal_t
  - 包含字段：id / bizNo / bizType / postingDate / currency / status / totalAmount / description / chainId / chainTxHash / blockNumber / tokenAddress / confirmations / reversedBy / version / 审计字段 / tenantId
  - 唯一索引：uk_biz_no；普通索引：idx_posting_date / idx_biz_type / idx_chain_tx / idx_status

- [ ] 4.2 新建 V3__ledger_entry.sql 明细表
  - 创建 docs/sql/V3__ledger_entry.sql
  - 包含字段：id / journalId / bizNo（冗余）/ accountCode / entryType / amount / uid / counterparty / balanceAfter / remark / 审计 / tenantId
  - 唯一索引：uk_journal_account_entry(biz_no, account_code, entry_type)
  - 普通索引：idx_journal / idx_biz_no / idx_uid / idx_account_code

- [ ] 4.3 新建 V4__account_accountcode.sql（account_t 加字段）
  - 创建 docs/sql/V4__account_accountcode.sql
  - ALTER TABLE account_t ADD COLUMN account_code VARCHAR(64) NOT NULL DEFAULT 'USER_AVAILABLE' AFTER uid
  - 添加 idx_account_code 索引

## 5. 基础设施 PO（Infrastructure PO）

- [ ] 5.1 创建 2 个 PO（LedgerJournalPO / LedgerEntryPO），通过 `mvn -pl otx-infrastructure compile`
  - 继承 BasePO
  - `@TableName("ledger_journal_t")` / `@TableName("ledger_entry_t")`
  - 字段与 SQL 一致；金额用 BigDecimal；日期用 LocalDate / LocalDateTime
  - 状态/EntryType/BizType 字段在 PO 中为 String 类型（与现有 FundFlowPO 风格一致）

## 6. Mapper 与 Converter

- [ ] 6.1 创建 2 个 Mapper 接口（LedgerJournalMapper / LedgerEntryMapper），通过 `mvn -pl otx-infrastructure compile`
  - 继承 `BaseMapper<LedgerJournalPO>` / `BaseMapper<LedgerEntryPO>`
  - 标注 `@Mapper`

- [ ] 6.2 创建 2 个 Converter（LedgerJournalConverter / LedgerEntryConverter），通过 `mvn -pl otx-infrastructure compile`
  - 基于 MapStruct 接口 + INSTANCE
  - `@Mapper(builder = @Builder(disableBuilder = true))`
  - 提供 po2Entity / entity2po / po2EntityList / entity2poList

## 7. 仓储实现（Repository Implementation）

- [ ] 7.1 实现 LedgerJournalRepoImpl，通过 `mvn -pl otx-infrastructure compile`
  - save：insert；findByBizNo：selectOne by bizNo；existsByBizNo：count by bizNo；update：updateById
  - 注入 LedgerJournalMapper + LedgerJournalConverter

- [ ] 7.2 实现 LedgerEntryRepoImpl，通过 `mvn -pl otx-infrastructure compile`
  - saveBatch：insertBatch（MyBatis-Plus 批量）；findByJournalId：selectList by journalId；findByBizNo：selectList by bizNo
  - 注入 LedgerEntryMapper + LedgerEntryConverter

## 8. 应用层 DTO（Application DTO）

- [ ] 8.1 创建 4 个 DTO，通过 `mvn -pl otx-application compile`
  - `PostJournalRequest`：bizNo / bizType / currency / postingDate / description / chainId / chainTxHash / blockNumber / tokenAddress / List<LedgerEntrySpec> entries
  - `LedgerEntrySpec`：accountCode / entryType / amount / uid / counterparty / balanceAfter / remark
  - `JournalDetailResponse`：id / bizNo / bizType / status / totalAmount / currency / postingDate / 链上字段 / List<LedgerEntryResponse> entries
  - `LedgerEntryResponse`：accountCode / entryType / amount / uid / counterparty / balanceAfter / remark

- [ ] 8.2 创建 LedgerAssembler（MapStruct），通过 `mvn -pl otx-application compile`
  - postJournalRequest2JournalEntity（注意 entries 转换）
  - journalEntity2JournalDetailResponse（含 entries 转换）
  - 单向映射（请求→实体、实体→响应），不实现反向

## 9. 应用服务（Application Service）

- [ ] 9.1 追加 12 个错误码到 BizErrorEnum + 创建 LedgerAppService 接口，通过 `mvn -pl otx-common compile && mvn -pl otx-application compile`
  - 错误码：LEDGER_ENTRIES_EMPTY / LEDGER_NOT_BALANCED / LEDGER_DUPLICATE_ACCOUNT / LEDGER_BIZ_NO_EMPTY / LEDGER_CURRENCY_EMPTY / LEDGER_AMOUNT_INVALID / LEDGER_ENTRY_TYPE_INVALID / LEDGER_ACCOUNT_CODE_INVALID / LEDGER_JOURNAL_NOT_FOUND / LEDGER_JOURNAL_NOT_DRAFT / LEDGER_JOURNAL_NOT_POSTED / LEDGER_REVERSAL_NOT_FOUND
  - 接口方法：`JournalDetailResponse postJournal(PostJournalRequest req)`、`JournalDetailResponse findByBizNo(String bizNo)`

- [ ] 9.2 实现 LedgerAppServiceImpl.postJournal() 入口（幂等检查 + 调用 atomic），通过 `mvn -pl otx-application compile`
  - 入参校验：bizNo 非空、currency 非空、entries 非空
  - 幂等检查：journalRepo.existsByBizNo → 命中直接查询返回
  - 调用 self.postJournalAtomic(req) 触发 AOP 代理
  - 通过 `@Lazy @Resource` 注入 self

- [ ] 9.3 实现 LedgerAppServiceImpl.postJournalAtomic() 事务块，通过 `mvn -pl otx-application test`
  - `@Retryable(retryFor={OptimisticLockException.class}, maxAttempts=5, backoff=@Backoff(delay=100, multiplier=1.5, maxDelay=500, random=true))`
  - `@Transactional(propagation=REQUIRES_NEW, rollbackFor=Exception.class)`
  - 构造 Entity（装配 entry 列表）→ 不变量校验 → journalRepo.save → entryRepo.saveBatch → journal.post() → journalRepo.update
  - catch DuplicateKeyException 转换为 BizIdempotentException
  - 单元测试覆盖：正常过账、DuplicateKey 转换 BizIdempotent、OptimisticLock 重试

- [ ] 9.4 实现 LedgerAppServiceImpl.findByBizNo()，通过 `mvn -pl otx-application compile`
  - 查 journalRepo.findByBizNo → null 抛 LEDGER_JOURNAL_NOT_FOUND
  - 查 entryRepo.findByBizNo → 组装 JournalDetailResponse
  - 装配：LedgerAssembler.journalEntity2JournalDetailResponse

## 10. 接口层（Interface）

- [ ] 10.1 创建 LedgerController，通过 `mvn -pl otx-interface compile && mvn -pl otx-interface test`
  - `@RestController @RequestMapping("/ledger/journals")`
  - `POST /` postJournal
  - `GET /{bizNo}` findByBizNo
  - 统一 Result<T> 响应
  - SpringDoc `@Operation` / `@Tag` 注解
  - 接口层单测：Mock LedgerAppService 验证路由分发

## 11. 集成测试（Integration Test）

- [ ] 11.1 创建 LedgerIntegrationTest，通过 `mvn -pl otx-starter test`
  - 3 件套：
    1. context load 测试（启动 Spring 上下文验证装配完整）
    2. 借贷平衡异常端到端（POST /ledger/journals + 借贷不等 + 断言 BizException 错误码）
    3. 并发幂等集成测试（10 个并发请求同一 bizNo，最终数据库仅 1 条 Journal）
  - 使用 @SpringBootTest
  - 测试环境配置沿用 otx-starter 既有约定
