package io.github.open55.mockupstream.scenario;

/**
 * 充值故事执行产出（供演示驱动衔接后续故事线）。
 *
 * @param uid    充值用户标识（后续提现故事线复用该用户）
 * @param result 充值故事执行结果
 */
public record DepositOutcome(Long uid, StoryResult result) {
}
