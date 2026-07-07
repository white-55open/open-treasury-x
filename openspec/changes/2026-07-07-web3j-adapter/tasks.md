# 任务清单：Web3j 链上查询适配器

> ## 执行流程（强制契约）
>
> ### 默认流程（逐个 task 执行）
>
> 1. **执行**：实现该 task 标明的所有内容（代码 + 测试 + 配置）。
> 2. **验证**：执行 task 末尾的 mvn 验证命令，必须编译成功且相关测试通过。
> 3. **展示变更摘要**：向用户输出新增/修改文件清单（带路径）、编译/测试结果、关键设计决策与注意事项。
> 4. **提醒人工 review**：提示用户 review 代码（目标 10 分钟内）。
> 5. **用户提交 git commit**：用户 review 确认无误后，手动执行 `git commit`（message 中英双语，如 `feat(infra): 实现 Web3jChainQueryAdapter / feat(infra): implement Web3jChainQueryAdapter`）。
> 6. **用户确认 continue**：用户输入 `continue` 后，继续下一个 task。
>
> **禁止**：跨 task 批量实现；跳过编译验证；用户未 ack 进入下一个。
> **回滚**：任意 task 出问题，`git revert` 即可，已提交历史不被破坏。
> 颗粒度规则：每个 task 单一目标、AI 实现 ≤30 分钟、人工 review ≤10 分钟、对应一次 commit。
> 测试要求：基础设施层 task 必须配单元测试（Mock web3j 客户端）；common 层 task 通过编译验证。

## 1. 错误码与异常

- [ ] 1.1 在 `BizErrorEnum` 追加 `LEDGER_CHAIN_NOT_CONFIGURED`，通过 `mvn -pl otx-common compile`
  - code: `LEDGER_CHAIN_NOT_CONFIGURED`
  - message: "未配置目标链的 RPC 节点"

- [ ] 1.2 创建 `Web3jRpcException`，通过 `mvn -pl otx-common compile`
  - 继承 `BizException`，包路径：`io.github.open55.otx.common.exception.blockchain`
  - 构造参数：`String chainId`、`List<String> failedUrls`、`Throwable cause`
  - `getMessage()` 返回人类可读的错误描述，包含链 ID 和失败的 URL 列表

## 2. 配置与客户端创建

- [ ] 2.1a 创建 `Web3jProperties`，通过 `mvn -pl otx-infrastructure compile`
  - `@ConfigurationProperties(prefix = "web3j")`
  - 字段：
    - `chainId`：String
    - `rpcUrls`：List<String>
    - `readTimeout`：long（默认 5000）
  - 提供 `getRpcUrls()` 返回不可修改列表

- [ ] 2.1b 创建 `Web3jConfig`，通过 `mvn -pl otx-infrastructure compile`
  - `@Configuration` + `@EnableConfigurationProperties(Web3jProperties.class)`
  - `@Bean web3jPool()`：返回 `Map<String, Web3j>`，key 为 RPC URL，value 为 `Web3j.build(new HttpService(url))`
  - 遍历 `properties.getRpcUrls()`，保持 `LinkedHashMap` 顺序（与配置中 RPC 节点顺序一致）
  - `@PreDestroy shutdown()`：遍历 pool values 调用 `web3j.shutdown()`

## 3. Web3jChainQueryAdapter 核心实现

- [ ] 3.1 实现 `Web3jChainQueryAdapter` 骨架 + `queryTxReceipt`，通过 `mvn -pl otx-infrastructure compile`
  - 实现 `ChainQueryPort` 接口
  - 注入 `Map<String, Web3j> web3jPool` + `Web3jProperties`
  - `queryTxReceipt(chainId, txHash)`：
    - chainId 不匹配 `properties.getChainId()` → 抛 `BizException(LEDGER_CHAIN_NOT_CONFIGURED)`
    - 按 `properties.getRpcUrls()` 顺序遍历，从 `web3jPool` 中取出对应的 `Web3j` 实例：
      - 调用 `web3j.ethGetTransactionReceipt(txHash).send()`
      - 成功 → 构造 `ChainTxReceipt` 并返回
      - 网络异常（`SocketTimeoutException` / `ConnectException`）→ 继续下一节点
    - 全部失败 → 抛 `Web3jRpcException`

- [ ] 3.2 实现 `currentBlockNumber` + `isConfirmed`，通过 `mvn -pl otx-infrastructure compile`
  - `currentBlockNumber(chainId)`：复用故障切换循环，调用 `web3j.ethBlockNumber().send()`
  - `isConfirmed(chainId, txHash, requiredConfirmations)`：
    - 调用 `queryTxReceipt(chainId, txHash)` 拿到交易块高
    - 调用 `currentBlockNumber(chainId)` 拿到当前块高
    - 计算确认数并比较

## 4. 单元测试

- [ ] 4.1 编写 `Web3jChainQueryAdapterTest`，通过 `mvn -pl otx-infrastructure test`
  - 测试方法按场景分组（`@Nested`）：
    - `queryTxReceipt`：
      - chainId 不匹配抛 LEDGER_CHAIN_NOT_CONFIGURED
      - 首个 RPC 节点成功返回 ChainTxReceipt
      - 首个节点超时，第二个节点成功返回
      - 所有节点失败抛 Web3jRpcException
      - 链上查不到交易返回 `Optional.empty()`
    - `currentBlockNumber`：
      - 正常返回区块高度
      - 节点故障切换
    - `isConfirmed`：
      - 确认数满足要求返回 true
      - 确认数不足返回 false
  - Mock 策略：Mock `Web3j` 接口 + `Request<?, ?>` 发送器，避免真实网络调用

## 5. application.yaml 配置

- [ ] 5.1 追加 web3j 配置段到 `application-dev.yaml`，通过 `mvn -pl otx-starter compile`
  - `chain-id: "11155111"`（Sepolia 测试网）
  - `rpc-urls[0]: "https://eth-sepolia.g.alchemy.com/v2/${ALCHEMY_KEY}"`
  - `read-timeout: 5000`
