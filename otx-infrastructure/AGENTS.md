# otx-infrastructure（基础设施层）

## OVERVIEW
出站适配器层：实现 otx-domain 端口，依赖 domain + common，**不依赖 application**；根包 `io.github.open55.otx.infrastructure`，9 子包 27 个 main 类（全项目最大模块），覆盖持久化 / 区块链 / 雪花 ID / 异常 / 审计。

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 持久化四件套（PO/Mapper/Converter/RepoImpl） | `po/`、`mapper/`、`converter/`、`repository/` 各 4~5 类，同名对齐 |
| 主键回写范式 | `repository/LedgerJournalRepoImpl.save`（insert 后写回 Entity） |
| 乐观锁双保险 | `config/MyBatisConfig`（内置 OptimisticLockerInnerInterceptor）+ `component/db/OptimisticLockerExceptionInterceptor`（影响 0 行抛异常） |
| 审计自动填充 | `component/db/AuditMetaObjectHandler`（createTime/lastUpdateTime/createdBy/lastUpdatedBy，userId 硬编码 1L） |
| 链上查询 | `blockchain/Web3jChainQueryAdapter`（实现 ChainQueryPort，多 RPC 按序切换）+ `blockchain/Web3jProperties`/`Web3jConfig` |
| 雪花 ID | `component/id/SnowflakeIdGeneratorImpl`（双实现：MP IdentifierGenerator + common 端口）+ `config/SnowflakeIdConfig` |
| 异常归一 | `component/exception/GlobalExceptionHandler`（BizException→HTTP 200+业务码；剥 MyBatisSystemException/PersistenceException 根因识别 OptimisticLockException） |
| 启动校验 | `config/DatabaseStartupValidationConfig`（SmartInitializingSingleton 验 DB 连接） |

## CONVENTIONS
- **四件套规则（本模块核心）**：新增业务域/聚合时，必须 `PO + Mapper + Converter + RepoImpl` 四类平行添加，缺一不可
- **乐观锁双保险分工**：内置 `OptimisticLockerInnerInterceptor` 负责 `@Version` 条件更新；自定义 `OptimisticLockerExceptionInterceptor`（StatementHandler 插件）把 update 影响 0 行转 `OptimisticLockException`，上层 `@Retryable` 才能捕获重试
- **主键回写**：RepoImpl.save 后必须回写 Entity 主键（以 LedgerJournalRepoImpl 为范式）
- **枚举 String 化**：PO 枚举字段一律 String，由 Converter 负责与 Entity 枚举互转；PO 层禁止出现枚举类型
- **仓储边界**：RepoImpl 只接受/返回 Entity，PO 绝不外泄到 domain 层；仓储接口层只能出现 Entity
- **全注解式 SQL**：无任何 mapper XML；Mapper 均 `extends BaseMapper<XxxPO>`，无自定义 SQL
- **幂等键**：fund_flow_t 的 bizNo 唯一索引（uk_biz_no）是幂等兜底；LedgerEntryPO 冗余 bizNo 防 join
- **转换规范**：Converter 为 MapStruct 接口（静态 INSTANCE + `@Builder(disableBuilder=true)`）；LedgerEntryConverter 的 entity2po ignore journalId/bizNo（由仓储层填充）
- **Bean 组装**：RepoImpl 用 `@Repository`，配置类集中在 `config/`；Web3j 实例按 RPC URL 池化，`@PreDestroy` 关闭

## ANTI-PATTERNS
- 仓储接口接受/返回 PO（PO 仅存于基础设施层，通过 Converter 隔离）
- 绕过 Mapper/RepoImpl 直接操作 PO 状态（Entity 变更必须走聚合根方法）
- 新增业务域时只写 RepoImpl 或只写 PO，破坏四件套完整性
- 手工 new 雪花 ID / Web3j / 审计填充逻辑，必须走既有组件
- 在 PO 层定义枚举类型字段或手写 getter/setter 样板（由 MP + MapStruct 代劳）
- 添加自定义 SQL 需要手写 XML 的做法（本模块零 XML，保持全注解基线）
