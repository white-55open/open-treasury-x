# 提案：Web3j 链上查询适配器

## 动机

Ledger 模块已在 domain 层定义 `ChainQueryPort` 出站端口和 `ChainTxReceipt` 值对象，`pom.xml` 也已依赖 `web3j 5.0.3`（含 CVE 修复），但该端口**无任何实现类**——链上交易回执查询、区块高度获取、确认数判断三个方法均无法工作。

当前 `ledger_journal_t` 的 `chain_tx_hash` / `chain_id` / `block_number` 等字段已预留，但缺少运行时能力将链上数据拉入系统。本变更填补这一空白，让 `ChainQueryPort` 从"空接口"变为"可工作的基础设施"。

## 设计方案

### 新增能力

- **`Web3jChainQueryAdapter`**：实现 `ChainQueryPort` 的三个方法，基于 web3j 调用以太坊兼容链的 JSON-RPC 接口
- **多节点故障切换**：单链可配置多个 RPC URL，按序尝试，失败时自动切换到下一节点
- **`Web3jRpcException`**：新增异常，在所有 RPC 节点均失败时抛出，携带失败详情

### 修改能力

- `BizErrorEnum`：追加 `LEDGER_CHAIN_NOT_CONFIGURED` 错误码
- `application-dev.yaml`：追加 `web3j` 配置段

### 配置模型

```yaml
web3j:
  chain-id: "11155111"               # Sepolia 测试网
  rpc-urls:                          # 多个 RPC 节点，严格按序故障切换
    - https://eth-sepolia.g.alchemy.com/v2/${ALCHEMY_KEY}
  read-timeout: 5000                 # 读取超时（毫秒）
```

### 故障切换逻辑

```
queryTxReceipt(chainId, txHash)
  ├─ chainId != 配置的 chain-id? → 抛 LEDGER_CHAIN_NOT_CONFIGURED
  └─ for url in rpc-urls:
       ├─ 创建 Web3j 实例 → 调用 eth_getTransactionReceipt
       ├─ 成功? → 返回 ChainTxReceipt
       ├─ 网络/超时异常? → 继续下一节点
       └─ 全部失败? → 抛 Web3jRpcException
```

### 受影响代码

| 层 | 新增/修改 |
|---|---|
| `otx-common` | `BizErrorEnum` 追加 `LEDGER_CHAIN_NOT_CONFIGURED`；新增 `Web3jRpcException` |
| `otx-infrastructure` | 新增 `blockchain/` 包：`Web3jProperties`、`Web3jConfig`、`Web3jChainQueryAdapter` |
| `otx-starter` | `application-dev.yaml` 追加 `web3j` 配置段 |

## 非目标

- ❌ **不接入任何调用者**：`ChainQueryPort` 的实现可工作，但不修改任何现有业务代码（LedgerAppService、DepositAppService 等）来调用它
- ❌ **不实现多链策略模式扩展**：当前仅支持单链硬编码 @Bean，多链路由留待后续变更
- ❌ **不实现 WebSocket 订阅**：仅 HTTP JSON-RPC 轮询，不涉及 pending transaction 监听或新块订阅
- ❌ **不实现写操作**：不涉及 `eth_sendRawTransaction` 等链上交易发送能力
- ❌ **不涉及领域事件**：ChainQueryPort 的调用时机与事件驱动无关

### 影响范围

#### 限界上下文

- **ledger** 上下文：`ChainQueryPort` 从"无实现"变为"可工作"，但 ledeger 业务逻辑本身不变
- 其他上下文不受影响

#### 架构层

- Domain 层：无变化（接口和值对象已存在）
- Infrastructure 层：新增 `blockchain` 适配器包，遵循出站适配器模式
- 仅新增代码，零修改既有文件（除 `BizErrorEnum` 追加枚举值和 `application-dev.yaml` 追加配置）

#### 兼容性

- 完全向后兼容：既有 `ChainQueryPort` 接口不变，新增实现类不影响任何已有 Bean
- 不修改任何数据库表或迁移脚本

#### 风险

| 风险 | 缓解 |
|---|---|
| RPC 节点全部不可用导致业务阻塞 | `Web3jRpcException` 明确告知失败原因；调用者可根据业务需求决定是否降级 |
| chainId 不匹配导致调用者传错链 | `LEDGER_CHAIN_NOT_CONFIGURED` 错误码清晰表达问题 |
| web3j 版本升级导致 API 不兼容 | web3j 5.0.3 为稳定版，核心 JSON-RPC 接口（eth_getTransactionReceipt / eth_blockNumber）长期稳定 |
