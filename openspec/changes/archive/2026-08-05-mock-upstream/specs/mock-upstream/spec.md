# 模拟上游（mock-upstream）规格

## ADDED Requirements

### Requirement: 系统必须提供独立的模拟上游模块

系统必须（MUST）在仓库根提供独立目录 `mock-upstream/`，作为 OTX 的外部调用方模拟器：独立的 Spring Boot 应用（独立 pom、独立端口 8004），**只通过 HTTP 调用 OTX REST API**，不得 import 任何 OTX 内部 Maven 模块或共享类。

#### Scenario: 独立启动

- **WHEN** 在 `mock-upstream/` 目录执行 Maven 启动命令
- **THEN** 模拟器作为独立进程启动（端口 8004），不依赖 OTX 的构建产物，仅需要 OTX 已在 8003 运行

#### Scenario: 只通过 HTTP 与 OTX 交互

- **WHEN** 模拟器执行任何业务动作
- **THEN** 所有与 OTX 的交互必须经由 HTTP REST 调用（`otx.base-url` 配置指向 OTX），源码中不得出现 OTX 模块的 import 或类型引用

### Requirement: 系统必须模拟链上事件与确认数演化

系统必须（MUST）提供链上事件模拟器：内存维护区块高度（定时增长）与模拟交易，交易确认数随区块推进递增；当确认数达到配置阈值时，自动触发 OTX 充值入账。

#### Scenario: 充值交易确认达标后自动入账

- **WHEN** 模拟器生成一笔充值交易，且其确认数达到配置阈值（默认 12）
- **THEN** 模拟器必须自动调用 `POST /deposit`（携带 uid/amount/bizNo/currency/chainId/chainTxHash）
- **THEN** 日志必须输出该步骤的调用端点与 OTX 响应结果

#### Scenario: 确认数未达标不入账

- **WHEN** 一笔充值交易的确认数尚未达到阈值
- **THEN** 模拟器不得调用充值入账接口，且日志展示当前确认数/所需确认数

### Requirement: 系统必须自动演绎提现业务故事线

系统必须（MUST）提供提现故事线编排器，自动按顺序调用 OTX 提现接口，覆盖"冻结 → 广播 → 确认结算"与"冻结 → 取消解冻"两条业务路径；每个步骤必须输出中文说明、调用端点与响应结果。

#### Scenario: 结算故事线完整执行

- **WHEN** 演示启动并执行结算故事线
- **THEN** 模拟器必须依次调用 `POST /withdraw/freeze`、`POST /withdraw/broadcast`、`POST /withdraw/{bizNo}/confirm-settle`
- **THEN** 每个步骤日志包含中文说明、HTTP 端点与 OTX 响应（code/data/message）

#### Scenario: 取消故事线完整执行

- **WHEN** 演示启动并执行取消故事线
- **THEN** 模拟器必须依次调用 `POST /withdraw/freeze`、`POST /withdraw/{bizNo}/cancel`
- **THEN** 日志输出每个步骤的调用端点与响应结果

### Requirement: 系统必须提供一键演示模式

系统必须（MUST）提供演示模式：应用启动后自动顺序执行充值故事线、提现结算故事线、提现取消故事线，步骤间有可配置延时，便于配合 admin-console 实时观察 OTX 落账效果。

#### Scenario: 启动即自动演绎

- **WHEN** 模拟器应用启动且 OTX 可用
- **THEN** 模拟器自动依次执行充值 → 结算 → 取消三条故事线
- **THEN** 全程日志可读（中文说明 + 端点 + 响应），结束后输出总结（成功/失败步骤统计）

#### Scenario: 幂等重复演绎

- **WHEN** 以相同 bizNo 重复执行某故事线
- **THEN** OTX 幂等语义生效（不重复入账/不重复动账），模拟器日志展示幂等返回结果
