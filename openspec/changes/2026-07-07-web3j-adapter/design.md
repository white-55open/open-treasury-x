# 设计：Web3j 链上查询适配器

## 上下文

Ledger 模块一期（2026-07-06）已在 domain 层定义 `ChainQueryPort` 出站端口和 `ChainTxReceipt` 值对象，web3j 5.0.3 也已作为基础设施依赖引入。但 `ChainQueryPort` 无任何实现类，三个方法（`queryTxReceipt` / `currentBlockNumber` / `isConfirmed`）均无法工作。

本变更聚焦基础设施层，实现一个**生产可用的 web3j 适配器**，具备多节点故障切换能力，为后续业务层调用（Ledger 过账前置验证、充值监控定时任务、链上对账等）提供可靠的基础。

## 目标 / 非目标

**目标：**

- 实现 `ChainQueryPort` 的全部三个方法
- 支持单链多个 RPC 节点，采用 Fast Failover 策略
- 所有 RPC 调用失败时抛出明确的业务异常
- 提供完整的单元测试覆盖（Mock web3j 客户端）

**非目标：**

- 不实现多链策略模式扩展（本期仅 @Bean 硬编码单链）
- 不实现 WebSocket 订阅推送
- 不写入链操作（eth_sendRawTransaction 等）
- 不修改任何业务层代码

## 决策

### 决策 1：包结构 = `otx-infrastructure/.../blockchain/`

**理由**：
- 基础设施层适配器集中在 `otx-infrastructure` 模块
- 新建 `blockchain` 包与现有 `repository` / `config` / `component` 平级，语义清晰
- 后续多链扩展时可在此包下添加 `MultiChainRouter` 等类，不散落

```
io.github.open55.otx.infrastructure.blockchain
  ├── Web3jProperties.java           # @ConfigurationProperties
  ├── Web3jConfig.java               # @Configuration
  └── Web3jChainQueryAdapter.java    # ChainQueryPort 实现
```

### 决策 2：配置结构 = 单链多节点，`@ConfigurationProperties` 嵌套对象

**理由**：
- 遵循项目现有模式（参考 `SnowflakeIdConfig` 的 `@ConfigurationProperties`）
- 嵌套对象结构清晰，易于扩展为多链（后续只需将 `chain-id` 改为 `chains` Map 即可）

```yaml
web3j:
  chain-id: "11155111"               # Sepolia 测试网
  rpc-urls:
    - https://eth-sepolia.g.alchemy.com/v2/xxx
  read-timeout: 5000
```

### 决策 3：Fast Failover（每次从第一个节点开始尝试）

**理由**：
- 实现简单，可预测性强
- 适合 RPC 节点故障为低频事件的场景
- 后续可升级为 Sticky 模式或健康检查，不影响接口契约

```
queryTxReceipt(chainId, txHash)
  for each url in rpc-urls:
    try:
      Web3j web3j = buildWeb3j(url)
      return web3j.ethGetTransactionReceipt(txHash).send()
    catch (SocketTimeoutException | ConnectException e):
      continue  // 尝试下一节点
  throw Web3jRpcException("all RPC nodes failed")
```

### 决策 4：失败判定 = 严格模式（仅网络/超时异常才切换）

**理由**：
- HTTP 5xx 可能是节点临时过载，不一定代表节点永久不可用
- 但 `SocketTimeoutException` 和 `ConnectException` 明确表示节点不可达
- 严格模式避免因节点返回业务错误（如 rate limit）而误切

### 决策 5：chainId 不匹配 = 抛 `LEDGER_CHAIN_NOT_CONFIGURED`

**理由**：
- 静默忽略（返回 `Optional.empty()`）会让调用者误以为交易在链上不存在
- 抛异常明确告知调用者：你请求的链未配置

### 决策 6：`isConfirmed` 委托 `queryTxReceipt` + `currentBlockNumber`

**理由**：
- 避免重复实现 RPC 调用逻辑
- `queryTxReceipt` 已包含交易所在块高
- `currentBlockNumber` 获取最新块高
- 确认数 = currentBlockNumber - txBlockNumber（+1）

```
isConfirmed(chainId, txHash, requiredConfirmations)
  receipt = queryTxReceipt(chainId, txHash)  // 含故障切换
  current = currentBlockNumber(chainId)       // 含故障切换
  confirmations = current - receipt.blockNumber + 1
  return confirmations >= requiredConfirmations
```

### 决策 7：Web3j 客户端池化——每个 RPC URL 对应一个共享实例

**理由**：
- `Web3j` 底层基于 OkHttpClient，后者是线程安全且自带连接池的——创建/关闭新实例会反复重建 TCP 连接和线程池，造成不必要的 GC 压力和延迟
- 池化后 TCP keep-alive、连接复用、DNS 缓存等机制自然生效
- `Web3j` 接口本身是线程安全的，同一实例可被多个请求共享

**实现方案**：

```
Web3jConfig
  └─ @Bean web3jPool(): Map<String, Web3j>
       ├─ url[0] → Web3j.build(new HttpService(url[0]))  // 共享实例
       ├─ url[1] → Web3j.build(new HttpService(url[1]))
       └─ 启动时一次性创建，永不重建

Web3jChainQueryAdapter
  ├─ 注入 Map<String, Web3j>
  ├─ queryTxReceipt():
  │    for entry in pool (保持 properties.rpcUrls 顺序):
  │        try: entry.getValue().ethGetTransactionReceipt(txHash).send()
  │        catch 网络异常: continue
  └─ @PreDestroy: 遍历 pool 关闭所有 Web3j 实例
```

**生命周期管理**：
- 启动时创建，应用关闭时 `web3j.shutdown()` 优雅关闭
- `Web3jConfig` 实现 `@PreDestroy` 遍历 `Map<String, Web3j>` 的 values 调用 `shutdown()`

**线程安全**：
- `Web3j` 实现（`JsonRpc2_0Web3j`）的所有公开方法同步访问内部 `Request` 构建器，且 `OkHttpClient` 天然线程安全
- 同一实例并发调用 `eth_getTransactionReceipt` / `eth_blockNumber` 安全

## 风险 / 权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| 池中 Web3j 实例泄漏（未正确 shutdown） | 应用重启前连接无法释放 | `@PreDestroy` 统一关闭 + `ShutdownHook` 兜底 |
| RPC 节点全部不可用 | queryTxReceipt / currentBlockNumber 均失败 | `Web3jRpcException` 明确告知失败；上层业务可降级 |
| chainId 硬编码 | 调用者传入不同 chainId 时抛异常 | 后续策略模式升级为多链路由 |
| web3j 依赖的 Vert.x 版本兼容 | 已通过 pom.xml 的 CVE 修复覆盖 | 已验证 Vert.x 5.0.7 与 web3j 5.0.3 兼容 |

## 待定问题

1. **RPC 请求超时值如何确定？** — 当前配置 3000ms 为经验值，需在实际网络环境下验证调整
2. **节点故障恢复后是否需要自动重试？** — 当前实现仅 failover 不重试，失败即抛异常
