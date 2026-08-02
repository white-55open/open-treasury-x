package io.github.open55.otx.domain.withdraw;

import java.util.Optional;

/**
 * 提现请求聚合根仓储接口。
 * <p>
 * 操作 WithdrawRequestEntity 聚合根，提供 save / findByBizNo / existsByBizNo / update 方法。
 * 仓储只操作聚合根，参数与返回值只出现 Entity；实现类在基础设施层（WithdrawRequestPO / Mapper）。
 */
public interface WithdrawRequestRepo {

    /**
     * 持久化新的提现请求。
     *
     * @param entity 待持久化的提现请求聚合根
     */
    void save(WithdrawRequestEntity entity);

    /**
     * 按业务流水号查询提现请求。
     *
     * @param bizNo 业务流水号
     * @return 提现请求聚合根，不存在时返回 Optional.empty()
     */
    Optional<WithdrawRequestEntity> findByBizNo(String bizNo);

    /**
     * 判断指定业务流水号是否已存在。
     *
     * @param bizNo 业务流水号
     * @return true 表示已存在
     */
    boolean existsByBizNo(String bizNo);

    /**
     * 更新提现请求（如广播后状态与交易哈希变更、结算后状态变更）。
     *
     * @param entity 待更新的提现请求聚合根
     */
    void update(WithdrawRequestEntity entity);
}
