package io.github.open55.mockupstream.blockchain;

import io.github.open55.mockupstream.config.SimulatorProperties;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟链按需推进单测。
 * <p>
 * 覆盖范围：advanceTo 推进后确认数正确增长、未达标交易确认数不足、
 * 推进幂等（目标高度低于当前高度时不回退）、区块高度只在推进时变化。
 */
class BlockchainSimulatorTest {

    /**
     * 构造配置确认阈值为 12 的模拟链实例。
     *
     * @return 模拟链实例
     */
    private BlockchainSimulator simulator() {
        SimulatorProperties properties = new SimulatorProperties();
        properties.setRequiredConfirmations(12);
        return new BlockchainSimulator(properties);
    }

    /**
     * 交易打包后确认数为 1；推进至确认达标后确认数达到阈值。
     */
    @Test
    void advanceTo_pushesConfirmationsToThreshold() {
        BlockchainSimulator sim = simulator();
        SimulatedTx tx = sim.registerTx("0x1111", "0x2222", BigInteger.valueOf(1000L));
        assertThat(sim.confirmationsOf(tx.getTxHash())).isEqualTo(1);

        // 推进到确认达标所需高度：createdBlock + 阈值 - 1
        sim.advanceTo(tx.getCreatedBlock() + 11);

        assertThat(sim.confirmationsOf(tx.getTxHash())).isEqualTo(12);
    }

    /**
     * 未推进到目标高度时确认数不足（不达标不入账的前提）。
     */
    @Test
    void advanceTo_partial_pushLeavesConfirmationsBelowThreshold() {
        BlockchainSimulator sim = simulator();
        SimulatedTx tx = sim.registerTx("0x1111", "0x2222", BigInteger.valueOf(1000L));

        sim.advanceTo(tx.getCreatedBlock() + 5);

        assertThat(sim.confirmationsOf(tx.getTxHash())).isEqualTo(6);
        assertThat(sim.confirmationsOf(tx.getTxHash())).isLessThan(12);
    }

    /**
     * 推进幂等：目标高度低于当前高度时高度不回退、确认数不减少。
     */
    @Test
    void advanceTo_lowerTarget_isNoOp() {
        BlockchainSimulator sim = simulator();
        SimulatedTx tx = sim.registerTx("0x1111", "0x2222", BigInteger.valueOf(1000L));
        sim.advanceTo(tx.getCreatedBlock() + 11);

        sim.advanceTo(0);

        assertThat(sim.currentHeight()).isEqualTo(tx.getCreatedBlock() + 11);
        assertThat(sim.confirmationsOf(tx.getTxHash())).isEqualTo(12);
    }

    /**
     * 无推进动作时区块高度与确认数保持初始值（不再定时自动增长）。
     */
    @Test
    void noAdvance_heightStaysInitial() {
        BlockchainSimulator sim = simulator();
        SimulatedTx tx = sim.registerTx("0x1111", "0x2222", BigInteger.valueOf(1000L));

        assertThat(sim.currentHeight()).isEqualTo(tx.getCreatedBlock());
        assertThat(sim.confirmationsOf(tx.getTxHash())).isEqualTo(1);
    }
}
