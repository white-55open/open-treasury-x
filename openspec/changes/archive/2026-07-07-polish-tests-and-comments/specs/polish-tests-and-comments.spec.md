# polish-tests-and-comments 规范

## 变更说明

本变更为纯代码质量改进，不修改任何既有规范的业务行为。所有变更仅限于：

1. **新增单元测试文件**：按项目级 testing spec 的要求补全测试覆盖，不改变被测类的行为
2. **补全 Javadoc 注释**：按 config.yaml `rules.documentation` 的要求补全缺失的字段/方法注释，不改变代码语义

因此，本变更不涉及任何 spec 级需求的增删改。既有 account、fundflow、ledger 三份规范的验收标准、错误码契约、接口契约全部保持不变。
