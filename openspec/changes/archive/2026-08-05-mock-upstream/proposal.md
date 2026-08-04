# 模拟上游（mock-upstream）变更提案

## Why（动机）

OTX 是 Web3 财务中间件，其业务价值只有在上游业务系统（CEX 的充值服务、提现服务）与链上事件的配合下才能体现。但当前没有真实的调用方——所有业务流只能通过 curl 手工或集成测试验证，开发者无法直观看到：

- 链上充值事件如何触发 OTX 入账（确认数增长 → 通知入账）；
- 提现全流程如何被上游编排（冻结 → 广播 → 链上确认 → 结算/取消）；
- OTX 每次被调用后，账户/流水/凭证/提现单据如何联动变化。

本变更新增 **模拟上游模块**：一个独立运行的轻量 Spring Boot 应用（与 OTX 同仓库、不同端口），模拟"链上事件源 + 业务系统调用方"，通过 **HTTP 调用 OTX 的 REST API**（与真实上游完全同构），按脚本自动演完"充值入账 → 提现冻结 → 广播 → 确认结算"的完整故事线。配合 `admin-console` 变更（管理控制台），实现"系统自己演给你看"。

## What Changes（设计方案要点）

### 新增：独立模块 `mock-upstream/`（不加入 Maven reactor）

- 位置：仓库根 `mock-upstream/` 目录，独立 `pom.xml`（Spring Boot 独立应用），**不加入父 pom 的 modules**——它只通过 HTTP 依赖 OTX，不 import 任何 OTX 内部类，保持"外部调用方"的真实性；
- 端口：默认 `8004`（OTX 为 8003）；配置 `otx.base-url=http://localhost:8003`；
- 技术栈：Spring Boot（web 非必需，用 RestClient 调 OTX + @Scheduled 定时驱动）。

### 功能一：链上事件模拟器（BlockchainSimulator）

- 维护一个模拟链（区块高度随时间增长）；
- 生成模拟充值交易（txHash + 接收地址 + 金额），确认数随区块增长递增；
- 当确认数达到 OTX 配置的确认阈值（默认 12）时，**自动调用 `POST /deposit`**（携带 chainId/chainTxHash/amount/uid）完成充值入账。

### 功能二：业务系统模拟器（WithdrawSimulator）

- 自动编排完整提现故事线：
  1. 为用户充值（走链上事件模拟）；
  2. 发起提现申请 → 调 `POST /withdraw/freeze`；
  3. 广播 → 调 `POST /withdraw/broadcast`（签名/广播端口由 OTX 侧 dev 配置决定，模拟器只负责调用并展示响应）；
  4. 模拟链上确认达标 → 调 `POST /withdraw/{bizNo}/confirm-settle`；
  5. 另跑一条"取消"故事线：冻结后直接调 `POST /withdraw/{bizNo}/cancel` 解冻。
- 每个步骤打印中文说明 + 调用的端点 + 响应结果，形成可读的业务日志。

### 功能三：演示驱动

- 提供 `run-demo` 模式：一条命令启动后自动顺序执行"充值线 + 提现线 + 取消线"，控制台输出完整业务故事；
- 配合 `admin-console` 控制台，可实时查看每一步在 OTX 中的落账效果。

## Capabilities（能力）

### New Capabilities

- `mock-upstream`：模拟上游与链上事件能力——为 OTX 提供真实的 HTTP 调用方与链上确认演化，支撑业务显性化演示与端到端验证。

### Modified Capabilities

无（OTX 主项目零改动；本变更只新增独立目录）。

## Impact（影响）

- **仓库结构**：新增 `mock-upstream/` 独立目录（pom + src），不进入 6 模块 reactor，不影响 `mvnw clean install` 主构建；
- **OTX 主项目**：零改动（模拟器只调 REST API）；
- **文档**：README 增加"一键体验：启动 OTX + mock-upstream + 管理控制台"章节（可选，随本变更或单独 docs 提交）。

## 非目标（Non-Goals）

- **不做真实链上交互**：不连真实 RPC、不广播真实交易（OTX 侧签名/广播在 dev 配置下由 LocalKeystoreSigner/Web3j 适配器处理，模拟器不关心其内部）；
- **不做 UI**：输出为控制台日志（页面可视化由 admin-console 变更承担）；
- **不引入测试框架**：模拟器是运行工具，不写单测（其正确性由"日志输出 + 控制台核对"验证）；
- **不做并发压力模拟**：单线程顺序故事线即可，不做性能测试。
