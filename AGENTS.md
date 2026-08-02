# AGENT要求（全局，保留）
- 使用oh-my-openagent插件编排任务，实现多AGENT多线程并发处理，每子线程AGENT处理时间不宜过长，能同时完成意味着可以更快做下一步。
- 生成的注释、文档、AI 思考过程和回答均以中文为主；代码、标识符、命令及必要的技术术语保留原文。

---

# PROJECT KNOWLEDGE BASE

**Generated:** 2026-08-02
**Commit:** dbe1ebf | **Branch:** dev_colorful

## OVERVIEW
open-treasury-x（OTX）：开源 Web3 财务管理中间件。Java 21 + Spring Boot 4.0.6 + MyBatis-Plus 3.5.15 + web3j 5.0.3，DDD+六边形分层，6 个 Maven 模块，根包 `io.github.open55.otx`。业务域：账户 / 充值 / 提现 / 资金流水 / 总账 / 链上查询。核心不变量：复式记账借贷必平、流水 append-only、幂等（bizNo + 唯一索引）。

## STRUCTURE
```
open-treasury-x/
├── otx-common/          共享内核：异常体系、Result<T>、常量、接口（零业务逻辑）
├── otx-domain/          领域层：聚合根/实体/仓储接口/端口（纯 Java，仅依赖 common）
├── otx-application/     应用层：用例编排、DTO、Assembler、事务边界
├── otx-infrastructure/  基础设施：MyBatis-Plus 持久化、web3j、雪花ID、乐观锁
├── otx-interface/       REST 入口（4 个 Controller）+ WebMvc + TraceId 拦截器
├── otx-starter/         组合根：启动类 + 全部配置（application*.yaml、log4j2）
├── openspec/            OpenSpec 规格：config.yaml（约定单一事实源）+ specs/ + changes/
└── docs/sql/            Flyway 迁移脚本 V1~V4（集成测试依赖）
```
依赖链（严格单向）：`common ← domain ← {application, infrastructure} ← interface ← starter`。

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 业务规则/不变量 | `otx-domain/ledger/LedgerJournalEntity.java`（借贷必平/状态机/冲销）、`otx-domain/account/entity/AccountEntity.java`（冻结/解冻/提现） |
| 错误码（唯一事实源） | `otx-common/.../exception/BizErrorEnum.java` |
| 统一响应 | `otx-common/.../response/Result.java` |
| 全局异常处理 | `otx-infrastructure/.../component/exception/GlobalExceptionHandler.java` |
| 幂等实现范式 | `otx-application/account/.../AccountAppServiceImpl.changeAmountWithFundFlow` + 唯一索引 uk_biz_no + BizIdempotentException |
| 链上查询 | `otx-domain/ledger/port/ChainQueryPort` + `otx-infrastructure/blockchain/Web3jChainQueryAdapter` |
| 乐观锁 | BaseEntity/BasePO `@Version` + infrastructure 双拦截器 + 应用层 `@Retryable` |
| 架构/编码铁律 | `openspec/config.yaml`（307 行，本仓库规则权威来源） |
| 启动与配置 | `otx-starter/src/main/resources/application*.yaml` |
| 测试规范 | `openspec/specs/testing/spec.md`（9 条禁止清单） |

## CODE MAP（核心符号）
| 符号 | 类型 | 位置 | 角色 |
|------|------|------|------|
| `OpenTreasuryXApplication` | 启动类 | otx-starter | `@SpringBootApplication` + `@EnableRetry` |
| `LedgerAppServiceImpl.postJournalAtomic` | 应用服务 | otx-application/ledger | `REQUIRES_NEW` + `@Retryable(5次)` 过账原子方法 |
| `LedgerJournalEntity` | 聚合根 | otx-domain/ledger | assertBalanced / assertNoDuplicateAccount / post / reverse |
| `AccountEntity` | 聚合根 | otx-domain/account | freeze / unfreeze / withdraw / increaseBalance |
| `AccountAppServiceImpl.changeAmountWithFundFlow` | 应用服务 | otx-application/account | 余额变更+流水原子化（幂等） |
| `Web3jChainQueryAdapter` | 适配器 | otx-infrastructure/blockchain | 实现 ChainQueryPort，多 RPC 切换 |
| `*RepoImpl`（4 个） | 仓储实现 | otx-infrastructure/repository | MP CRUD + MapStruct 转换 + 主键回写 |
| `TraceIdInterceptor` | 拦截器 | otx-interface | 请求头/MDC traceId |
| `GlobalExceptionHandler` | 异常处理 | otx-infrastructure | BizException→HTTP 200+业务码 |

## CONVENTIONS（区别于标准 Java 的要点）
- **依赖铁律**：domain 纯 Java 零框架；interface 不直接依赖 domain；infrastructure 不依赖 application；禁止反向/循环依赖
- **统一响应**：所有业务响应 HTTP 200，错误用业务码区分（`Result<T>`）
- **异常层次**：`BizException(BizErrorEnum)` / `OptimisticLockException` / `BizIdempotentException` / `Web3jRpcException`
- **幂等三保险**：bizNo 前置检查 → DB 唯一索引 → BizIdempotentException 兜底
- **self 代理模式**：`@Transactional`/`@Retryable` 方法必须通过 `self` 调用以触发 Spring AOP（各 AppServiceImpl 均如此）
- **事务语义**：余额变更+流水同一 `REQUIRES_NEW`；总账过账失败**不回滚**余额/流水（最终一致性，靠后续对账修复）
- **继承**：实体必须继承 `BaseEntity`、PO 必须继承 `BasePO`；PO 枚举字段一律 String，由 MapStruct Converter 互转
- **金额**：一律 `BigDecimal` / `DECIMAL(38,18)`，冻结/提现/分录金额必须为正
- **命名**：表 `*_t`、唯一索引 `uk_*`、普通索引 `idx_*`；类后缀 Entity/PO/Repo/Mapper/Converter/AppService/Controller/DTO
- **注释与日志**：注释/文档/Javadoc 全中文（多行格式）；日志英文 + 每行上方中文注释；强制 `@Slf4j`
- **DTO 后缀**：新代码统一 DTO 后缀（旧类 `GetAccountResponse`/`ChangeAmountRequest` 未遵守，勿模仿）
- **无静态检查工具**（无 checkstyle/spotbugs/lombok.config）：质量靠 review + 测试规范

## ANTI-PATTERNS（本仓库明令禁止）
- 反向/循环模块依赖（domain 绝不 import application/infrastructure/interface）
- 仓储接口接受/返回 PO（接口层只能出现 Entity；PO 只存在于基础设施层）
- 绕过聚合根方法直接改状态（凭证状态必须走 `post()`/`reverse()`）
- 修改/删除资金流水（append-only）；原凭证不可删除，只能冲销
- 冻结/解冻/提现金额非正数或余额不足仍继续操作
- 手动 `new Logger`、单行字段注释、魔法数字、硬编码字符串
- 新增代码不得引入 TODO/FIXME/HACK/XXX 与 @Deprecated（当前代码库零标记，保持基线）
- 测试：禁止依赖执行顺序、禁止真实网络/DB 的单测、禁止无意义命名（详见 testing/spec.md）

## 已知设计债（编号见 `openspec/changes/archive/.../design.md`）
- **DEVT-004** withdraw 未前置 freeze（退化为直接扣可用余额）
- **DEVF-003** SETTLE 枚举缺失（表预留、枚举类无值）
- **DEVF-001** 应用层→应用层直调；**DEVT-005** BigDecimal 未封装 Money；**DEVF-002** FundFlowEntity 贫血；**DEVT-006** 多租户未启用

## COMMANDS
```bash
mvnw.cmd package -DskipTests              # 打包（跳过测试，日常构建）
mvnw.cmd test                             # 全量测试（集成测试需本地 MySQL:3307/Redis:6380 + Flyway 已迁移）
mvnw.cmd -pl otx-interface -am test       # 单模块测试（-am 连带构建依赖模块）
mvnw.cmd -pl otx-starter spring-boot:run  # 本地启动（默认 dev profile）
java -jar otx-starter/target/otx-starter-0.0.1-SNAPSHOT.jar
```
环境：Java 21 + Maven 3.9.16（wrapper 自动下载）。**无 CI 工作流、无 Dockerfile**，构建全本地。

## NOTES（易踩坑）
- `application-dev.yaml` 被 `.gitignore` 忽略，clone 后需本地准备才能启动
- 集成测试仅存在于 otx-starter（`@SpringBootTest` 连真实 MySQL/Redis），其余模块单测全 mock
- 无 mapper XML：SQL 全注解式（BaseMapper + LambdaQueryWrapper）；`resources/db/migration` 为空占位
- 无分页对象/分页约定：当前无分页能力，新增时需自建
- ledger 实体直接放 `domain.ledger` 根包，与 account/fundflow 的 `entity/` 子包结构不一致（历史遗留）
