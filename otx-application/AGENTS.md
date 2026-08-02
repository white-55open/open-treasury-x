# otx-application（应用层）

**生成:** 2026-08-02 | **根包:** `io.github.open55.otx.application` | **main 类数:** 20

## OVERVIEW
应用层（用例编排层），依赖 otx-domain，负责 DTO 校验、事务边界、幂等编排与 Entity↔DTO 装配，不含任何框架无关业务规则。

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 余额变更+流水幂等编排 | `account/service/impl/AccountAppServiceImpl.changeAmountWithFundFlow` |
| 原子化余额变更（事务+重试） | `AccountAppServiceImpl.changeAmountWithFundFlowAtomic` |
| 充值编排（含过账容错） | `deposit/service/impl/DepositAppServiceImpl.deposit` |
| 过账编排（含幂等） | `ledger/service/impl/LedgerAppServiceImpl.postJournal` |
| 凭证原子过账（事务+重试） | `LedgerAppServiceImpl.postJournalAtomic` |
| Entity↔DTO 互转 | `assembler/AccountAssembler`、`ledger/assembler/LedgerAssembler`（MapStruct INSTANCE） |
| 流水记录与幂等校验 | `fundflow/service/impl/FundFlowAppServiceImpl.record/existsBizNo` |

## CONVENTIONS
- **self 代理（本模块最核心）**：`AccountAppServiceImpl`/`DepositAppServiceImpl`/`LedgerAppServiceImpl` 均声明 `@Resource @Lazy private XxxAppServiceImpl self;`。`@Transactional`/`@Retryable` 方法必须经 `self.xxx()` 调用才触发 Spring AOP，直接 `this.xxx()` 注解失效。self 字段类型用 Impl 本身而非接口，绕开 JDK 动态代理限制。
- **事务边界**：
  - `AccountAppServiceImpl.changeAmountWithFundFlowAtomic`：`@Transactional(REQUIRES_NEW)` 余额变更+流水同事务 + `@Retryable(OptimisticLockException, maxAttempts=5, delay=100ms×1.5, maxDelay=250, random)`
  - `LedgerAppServiceImpl.postJournalAtomic`：`@Transactional(REQUIRES_NEW)` + `@Retryable(OptimisticLockException, maxAttempts=5, delay=100ms×1.5, maxDelay=500, random)`
- **幂等编排模式**：入口方法 `existsBizNo` 前置检查 → `self.原子方法()` → catch `BizIdempotentException` 忽略返回（`changeAmountWithFundFlow` 与 `postJournal` 同款三步）。
- **过账失败容错**：`DepositAppServiceImpl.deposit` 中 `ledgerAppService.postJournal` 抛异常仅 `log.warn`，余额+流水已提交不回滚，靠对账修复（最终一致性）。
- **Assembler**：MapStruct 接口 + `static INSTANCE`，DTO↔Entity 互转；流水号由 `SnowflakeIdUtil` 生成。
- **包结构**：5 业务域 `account/deposit/fundflow/ledger/withdraw`，各自 `service/`+`service/impl/`+`dto/request/`（+`dto/response/`）；`assembler/` 为顶层包。deposit/withdraw 在 domain 无对应包，复用 account/fundflow/ledger 领域能力。
- **测试**：5 个 `*AppServiceImplTest`，`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@Captor`/`@Nested`，私有字段用 `setField` 反射注入。

## ANTI-PATTERNS
- 应用层→应用层直调：`DepositAppServiceImpl` 注入 `AccountAppService`（DEVF-001 已知设计债，不复制该写法）
- `this.xxx()` 调用本类 `@Transactional`/`@Retryable` 方法（AOP 失效，必须走 self）
- 接口上标 `@Service`；实现类不放 `impl` 包
- 跳过 `changeAmountWithFundFlow`/`postJournal` 入口直接调原子方法（幂等检查丢失）
