package io.github.open55.mockupstream.web;

/**
 * 账户资产视图（供个人中心资产卡片展示）。
 * <p>
 * 由 OTX 账户查询响应的可用/冻结余额组装，OTX 不可达时展示占位符。
 *
 * @param availableBalance 可用余额文本（如 "100.000000000000000000"；查询失败为 "-"）
 * @param frozenBalance    冻结余额文本（查询失败为 "-"）
 */
public record AccountView(String availableBalance, String frozenBalance) {
}
