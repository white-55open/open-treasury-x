# 任务清单：Ledger 模块

> ## 实施节奏（强制契约）
>
> 每个 task 是独立的 review + 试运行单元，AI 与用户的契约如下：
>
> 1. **执行**：实现该 task 标明的所有内容（代码 + 测试 + 配置）。
> 2. **AI 自验**：执行 task 末尾的 mvn 验证命令，必须编译成功且相关测试通过。
> 3. **报告**：向用户输出：
>    - 新增/修改文件清单（带路径）
>    - 编译/测试结果
>    - 关键设计决策与注意事项
> 4. **人工 review**：用户 review 代码（目标 10 分钟内），自行试运行。
> 5. **Continue**：用户显式 ack 后，AI 才执行下一个 task。
> 6. **commit**：每 task ack 后，AI 自动 `git commit`（message 形如 `feat(ledger): 1.1 add 4 domain enums`）。
>
> **禁止**：跨 task 批量实现；跳过编译验证；用户未 ack 进入下一个。
> **回滚**：任意 task 出问题，`git revert` 即可，已提交历史不被破坏。
>
> 颗粒度规则：每个 task 单一目标、AI 实现 ≤30 分钟、人工 review ≤10 分钟、对应一次 commit。
> 测试要求：domain 层 task 必须配单元测试；infra/application task 通过 mvn test 验证；接口层 task 通过接口层单测验证。

## 1. 域枚举（Domain Enums）

- [x] 1.1 创建 4 个枚举类（LedgerJournalStatusEnum / LedgerEntryTypeEnum / LedgerBizTypeEnum / LedgerAccountCodeEnum），通过 `mvn -pl otx-domain compile`
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

- [ ] 2.3a 实现 `assertBalanced()`，通过 `mvn -pl otx-domain test`
  - 规则：sum(DEBIT) == sum(CREDIT)、DEBIT 至少一条、CREDIT 至少一条、entries 非空
  - 单元测试覆盖：金额不等抛 LEDGER_NOT_BALANCED、缺 DEBIT 抛 LEDGER_ENTRIES_EMPTY、缺 CREDIT 抛 LEDGER_ENTRIES_EMPTY

- [ ] 2.3b 实现 `assertNoDuplicateAccount()`，通过 `mvn -pl otx-domain test`
  - 规则：同 accountCode 同 entryType 不重复
  - 单元测试覆盖：同户同向重复抛 LEDGER_DUPLICATE_ACCOUNT

- [ ] 2.4 实现 Journal.post() 状态转换 DRAFT → POSTED，通过 `mvn -pl otx-domain test`
  - 仅 DRAFT 状态可 post；POSTED 重复 post 抛 LEDGER_JOURNAL_NOT_DRAFT
  - post() 后 status 字段更新
  - 单元测试覆盖：DRAFT 可 post、POSTED 重复 post 抛异常

- [ ] 2.5 实现 Journal.reverse(reason) 产生反向 Journal，通过 `mvn -pl otx-domain test`
  - 仅 POSTED 状态可 reverse；REVERSED 重复 reverse 抛 LEDGER_JOURNAL_NOT_POSTED
  - reverse 产生新 Journal（所有 entry 的 entryType 取反、bizNo 以 "RV-" 前缀），原 Journal 状态置 REVERSED，记录 reversedBy
  - 单元测试覆盖：POSTED 可 reverse、REVERSED 重复 reverse 抛异常、反向 Journal 借贷方向互换

## 3. 域服务与端口（Domain Service & Ports）

- [ ] 3.1a 创建 LedgerPostingService 接口（领域服务契约），通过 `mvn -pl otx-domain compile`
  - 方法签名：`void postJournal(LedgerJournalEntity journal)`
  - 仅定义接口与 Javadoc，不写实现

- [ ] 3.1b 实现 LedgerPostingServiceImpl（无状态编排），通过 `mvn -pl otx-domain test`
  - 实现：assertBalanced → assertNoDuplicateAccount
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

- [ ] 5.1a 创建 LedgerJournalPO，通过 `mvn -pl otx-infrastructure compile`
  - 继承 BasePO
  - `@TableName("ledger_journal_t")`
  - 字段与 V2 SQL 一致；金额用 BigDecimal；日期用 LocalDate / LocalDateTime
  - 状态字段在 PO 中为 String 类型（与现有 FundFlowPO 风格一致）

- [ ] 5.1b 创建 LedgerEntryPO，通过 `mvn -pl otx-infrastructure compile`
  - 继承 BasePO
  - `@TableName("ledger_entry_t")`
  - 字段与 V3 SQL 一致；金额用 BigDecimal
  - accountCode / entryType / bizNo 字段为 String 类型

## 6. Mapper 与 Converter

- [ ] 6.1 创建 2 个 Mapper 接口（LedgerJournalMapper / LedgerEntryMapper），通过 `mvn -pl otx-infrastructure compile`
  - 继承 `BaseMapper<LedgerJournalPO>` / `BaseMapper<LedgerEntryPO>`
  - 标注 `@Mapper`

- [ ] 6.2a 创建 LedgerJournalConverter（MapStruct），通过 `mvn -pl otx-infrastructure compile`
  - 基于 MapStruct 接口 + INSTANCE
  - `@Mapper(builder = @Builder(disableBuilder = true))`
  - 提供 po2Entity / entity2po / po2EntityList / entity2poList

- [ ] 6.2b 创建 LedgerEntryConverter（MapStruct），通过 `mvn -pl otx-infrastructure compile`
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

- [ ] 8.1a 创建入参 DTO（PostJournalRequest + LedgerEntrySpec），通过 `mvn -pl otx-application compile`
  - `PostJournalRequest`：bizNo / bizType / currency / postingDate / description / chainId / chainTxHash / blockNumber / tokenAddress / List<LedgerEntrySpec> entries
  - `LedgerEntrySpec`：accountCode / entryType / amount / uid / counterparty / balanceAfter / remark

- [ ] 8.1b 创建出参 DTO（JournalDetailResponse + LedgerEntryResponse），通过 `mvn -pl otx-application compile`
  - `JournalDetailResponse`：id / bizNo / bizType / status / totalAmount / currency / postingDate / 链上字段 / List<LedgerEntryResponse> entries
  - `LedgerEntryResponse`：accountCode / entryType / amount / uid / counterparty / balanceAfter / remark

- [ ] 8.2 创建 LedgerAssembler（MapStruct），通过 `mvn -pl otx-application compile`
  - postJournalRequest2JournalEntity（注意 entries 转换）
  - journalEntity2JournalDetailResponse（含 entries 转换）
  - 单向映射（请求→实体、实体→响应），不实现反向

## 9. 应用服务（Application Service）

- [ ] 9.1a 追加 12 个错误码到 BizErrorEnum，通过 `mvn -pl otx-common compile`
  - 错误码：LEDGER_ENTRIES_EMPTY / LEDGER_NOT_BALANCED / LEDGER_DUPLICATE_ACCOUNT / LEDGER_BIZ_NO_EMPTY / LEDGER_CURRENCY_EMPTY / LEDGER_AMOUNT_INVALID / LEDGER_ENTRY_TYPE_INVALID / LEDGER_ACCOUNT_CODE_INVALID / LEDGER_JOURNAL_NOT_FOUND / LEDGER_JOURNAL_NOT_DRAFT / LEDGER_JOURNAL_NOT_POSTED / LEDGER_REVERSAL_NOT_FOUND

- [ ] 9.1b 创建 LedgerAppService 接口，通过 `mvn -pl otx-application compile`
  - 接口方法：`JournalDetailResponse postJournal(PostJournalRequest req)`、`JournalDetailResponse findByBizNo(String bizNo)`

- [ ] 9.2a 实现 LedgerAppServiceImpl 骨架 + 入参校验 + 幂等检查，通过 `mvn -pl otx-application compile`
  - 入参校验：bizNo 非空、currency 非空、entries 非空
  - 幂等检查：journalRepo.existsByBizNo → 命中直接调用 findByBizNo 返回
  - 不写 postJournalAtomic 方法体（仅占位 throw UnsupportedOperationException）

- [ ] 9.2b 引入 `@Lazy @Resource self` 并实现 postJournal() 调度，通过 `mvn -pl otx-application compile`
  - 入口方法 `postJournal()` 完成入参校验 → 幂等检查 → 调用 self.postJournalAtomic(req)
  - self 注入用 `@Lazy` 避免循环依赖
  - 单元测试覆盖：自调用走代理

- [ ] 9.3a 实现 postJournalAtomic() 事务块主体（无 @Retryable），通过 `mvn -pl otx-application test`
  - `@Transactional(propagation=REQUIRES_NEW, rollbackFor=Exception.class)`
  - 构造 Entity（装配 entry 列表）→ 不变量校验 → journalRepo.save → entryRepo.saveBatch → journal.post() → journalRepo.update
  - 单元测试覆盖：happy path 正常过账

- [ ] 9.3b 加 @Retryable + DuplicateKey 处理 + 重试单测，通过 `mvn -pl otx-application test`
  - `@Retryable(retryFor={OptimisticLockException.class}, maxAttempts=5, backoff=@Backoff(delay=100, multiplier=1.5, maxDelay=500, random=true))`
  - catch DuplicateKeyException 转换为 BizIdempotentException
  - 单元测试覆盖：DuplicateKey 转换 BizIdempotent、OptimisticLock 重试

- [ ] 9.4 实现 LedgerAppServiceImpl.findByBizNo()，通过 `mvn -pl otx-application compile`
  - 查 journalRepo.findByBizNo → null 抛 LEDGER_JOURNAL_NOT_FOUND
  - 查 entryRepo.findByBizNo → 组装 JournalDetailResponse
  - 装配：LedgerAssembler.journalEntity2JournalDetailResponse

## 10. 接口层（Interface）

- [ ] 10.1a 创建 LedgerController 骨架 + POST / 路由，通过 `mvn -pl otx-interface compile && mvn -pl otx-interface test`
  - `@RestController @RequestMapping("/ledger/journals")`
  - `POST /` postJournal
  - 统一 Result<T> 响应
  - 接口层单测：Mock LedgerAppService 验证 POST 路由分发

- [ ] 10.1b 追加 GET /{bizNo} 路由，通过 `mvn -pl otx-interface compile && mvn -pl otx-interface test`
  - `GET /{bizNo}` findByBizNo
  - 接口层单测：Mock LedgerAppService 验证 GET 路由分发

- [ ] 10.1c 补 SpringDoc 注解（@Operation / @Tag），通过 `mvn -pl otx-interface compile`
  - POST 与 GET 端点补 `@Operation` 描述
  - Controller 类加 `@Tag(name = "ledger")`
  - 不影响既有单测通过

## 11. 集成测试（Integration Test）

- [ ] 11.1a 创建 context load 集成测试，通过 `mvn -pl otx-starter test`
  - `@SpringBootTest` 启动 Spring 上下文验证装配完整
  - 单独一个测试方法 `contextLoads()`

- [ ] 11.1b 创建借贷不平衡 E2E 集成测试，通过 `mvn -pl otx-starter test`
  - `POST /ledger/journals` 提交借贷不等的请求
  - 断言返回 `BizException` 且错误码 = `LEDGER_NOT_BALANCED`

- [ ] 11.1c 创建并发幂等集成测试，通过 `mvn -pl otx-starter test`
  - 10 个并发请求同一 bizNo
  - 最终数据库仅 1 条 Journal（其余 9 个走幂等分支返回同一条）
  - 测试环境配置沿用 otx-starter 既有约定
