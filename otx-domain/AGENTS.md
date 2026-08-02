# otx-domain（领域层）

## OVERVIEW
DDD 领域层，纯 Java 零框架注解，仅依赖 otx-common（BizException / BizErrorEnum / BaseEntity），承载全部业务规则与聚合根；根包 `io.github.open55.otx.domain`，共 19 个 main 类（ledger 12 / fundflow 4 / account 2 / common 1）。

## WHERE TO LOOK
| 业务规则 | 位置 |
|----------|------|
| 借贷必平 | `ledger/LedgerJournalEntity.assertBalanced()`（LEDGER_NOT_BALANCED） |
| 同户同向唯一 | `assertNoDuplicateAccount()`（LEDGER_DUPLICATE_ACCOUNT） |
| 凭证状态机 | `post()` 仅 DRAFT 可过账（LEDGER_JOURNAL_NOT_DRAFT）；`reverse()` 仅 POSTED 可冲销 |
| 分录金额为正 | `LedgerEntryEntity` 构造器（LEDGER_AMOUNT_INVALID） |
| 冻结 / 解冻 / 提现 | `account/entity/AccountEntity` 的 `freezeBalance`（FREEZE_AMOUNT_INVALID / INSUFFICIENT_BALANCE）、`unfreezeBalance`、`withdraw`、`increaseBalance`、`deposit` |
| 过账编排 | `ledger/service/LedgerPostingDomainService(Impl)` |
| 链上查询出站端口 | `ledger/port/ChainQueryPort` + `ChainTxReceipt`（本期无实现，预留 web3j） |
| 会计科目 | `ledger/enums/LedgerAccountCodeEnum`（10 科目）、LedgerBizTypeEnum（6 种）、LedgerEntryTypeEnum（DEBIT/CREDIT）、LedgerJournalStatusEnum（DRAFT→POSTED→REVERSED） |
| 仓储接口 | `account/repository/AccountRepo`、`fundflow/repository/FundFlowRepo`、`ledger/repository/LedgerJournalRepo + LedgerEntryRepo`（接口仅暴露 Entity） |

## CONVENTIONS
- **聚合根方法为唯一规则入口**：余额/状态变更必须调 AccountEntity、LedgerJournalEntity 的领域方法，规则校验全部收口在聚合根内，实体外不得内联业务规则。
- **域间禁止 import**：account / fundflow / ledger 三个业务域完全平行，仅共享 `common/entity/BaseEntity` 与 otx-common 异常；ledger 不 import account（USER_AVAILABLE 等概念科目仅语义对应，无代码级耦合）。域间协作一律由应用层编排。
- **领域服务零规则**：LedgerPostingDomainService 只复用聚合根方法做过账编排，不承载任何校验/状态逻辑。
- **结构不一致（历史遗留）**：ledger 的实体 `LedgerJournalEntity` / `LedgerEntryEntity` 直接放 `ledger` 根包，而 account / fundflow 用 `entity/` 子包；新增 ledger 相关类时沿用根包现状，勿混用两种结构。
- **贫血体**：`FundFlowEntity` 为纯数据无行为（DEVF-002 设计债），新增实体以 AccountEntity / LedgerJournalEntity 为范式。
- **仓储接口契约**：参数与返回值只用 Entity，不出现 PO（PO 属基础设施层，domain 不可见）。
- **测试**：6 个测试类纯 JUnit 5，直接 `new` 被测类，零 Mockito、零 Spring 上下文。

## ANTI-PATTERNS
- 域间相互 import（如 ledger → account）或经由 BaseEntity 之外的路径共享领域类型
- 绕过聚合根方法直接改状态（余额字段私有赋值、凭证状态直接 set）
- 领域服务内联规则逻辑，替代聚合根校验
- 给 FundFlowEntity 这类贫血体追加框架依赖或直接塞行为而不升级为聚合根
- 仓储接口泄漏 PO / Mapper，或把领域枚举转成 String 后外抛
