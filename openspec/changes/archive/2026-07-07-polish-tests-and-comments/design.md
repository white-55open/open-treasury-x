# 补全测试与注释 - 设计

## 上下文

项目已有 ledger 上下文的 5 个高质量测试文件（符合 testing spec 规范），但 account、fundflow、deposit、withdraw、infrastructure、common 等模块零测试覆盖。同时 config.yaml 的 `rules.documentation` 对 Javadoc 格式有严格定义（字段级多行中文 Javadoc、方法级中文说明、MapStruct 方法注释等），但现有代码并非百分之百达标。

本变更不修改任何业务逻辑、不新增 API、不修改数据库 schema。是纯粹的代码质量改进。

```
┌──────────────────────────────────────────────────────────────────┐
│  现有测试覆盖                                  变更后目标覆盖      │
│  ┌────────────┐          ┌────────────┐                         │
│  │ Domain     │ 3 UT     │ Domain     │ 5 UT  (+2)              │
│  │ Application│ 1 IT     │ Application│ 1 IT + 4 UT  (+4)      │
│  │ Interface  │ 1 IT     │ Interface  │ 1 IT + 3 UT  (+3)      │
│  │ Infrastruct│ 0        │ Infrastruct│ 12 UT (+12)             │
│  │ Common     │ 0        │ Common     │ 2 UT  (+2)              │
│  │ Starter    │ 2 IT     │ Starter    │ 2 IT  (不变)            │
│  ├────────────┼──────────┤────────────┼──────────┤              │
│  │ 总计       │ 7        │ 总计       │ 29       │              │
│  └────────────┘          └────────────┘                         │
└──────────────────────────────────────────────────────────────────┘
```

## 目标 / 非目标

**目标：**
- 为核心业务类和基础设施类补全单元测试，覆盖正常路径和所有错误码分支
- 按 testing spec 统一测试风格：`@DisplayName`（中英双语）、`@Nested` 分组、snake_case 方法名、命名常量、AAA 结构
- 按 config.yaml `rules.documentation` 补全缺失的中文 Javadoc

**非目标：**
- 不修改业务逻辑代码（Entity、Service、Controller 的实现体零改动）
- 不新增集成测试（依赖 Spring 容器、DB 的测试保持现有数量）
- 不新增端到端测试
- 不引入架构变更（领域事件、ChainQueryPort 等保持现状）

## 决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 测试框架 | JUnit 5 + Mockito + AssertJ | 与现有测试一致，无新依赖 |
| 领域层测试方式 | 纯 POJO 单元测试（零 Mock） | 领域层纯 Java，无外部依赖，直接 new 实体调用领域方法 |
| 应用层测试方式 | Mockito mock 仓储接口 | 应用服务依赖 Repo 端口，Mock 掉基础设施，聚焦编排逻辑 |
| Controller 测试 | MockMvc（非 @WebMvcTest） | 遵循现有 LedgerControllerTest 风格 |
| Converter 测试 | MapStruct 直接调用 + assertEquals | Converter 无依赖，直接验证映射结果 |
| RepoImpl 测试 | Mock Mapper + verify | RepoImpl 很薄，验证 Mapper 调用和转换逻辑 |
| Javadoc 补全方式 | 逐文件检查 + 按 rules.documentation 格式补全 | 保持一致性，不引入自动化工具 |

### 测试数据构造模式

统一使用 `private static` 工厂方法代替散落的魔法值：

```java
// 遵循现有 LedgerEntryEntityTest 风格
private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
private static final long TEST_UID = 12345L;

private static AccountEntity createDefaultAccount() {
    return AccountEntity.builder()
            .uid(TEST_UID)
            .availableBalance(new BigDecimal("1000"))
            .frozenBalance(BigDecimal.ZERO)
            .build();
}
```

## 风险 / 权衡

| 风险 | 缓解措施 |
|------|----------|
| 测试覆盖面积的广度可能导致测试深度不够 | 优先覆盖领域方法的每个分支（正常 + 每个错误码），AppService 的每个编排路径 |
| 测试文件过多导致构建时间增加 | 单元测试不启动 Spring 容器，test compile + exec 在秒级完成 |
| Javadoc 补全涉及大量文件，可能遗漏 | 以 tasks.md 中列出的文件清单逐文件确认 |
