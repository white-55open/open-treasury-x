package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.domain.withdraw.WithdrawRequestRepo;
import io.github.open55.otx.infrastructure.converter.WithdrawRequestConverter;
import io.github.open55.otx.infrastructure.mapper.WithdrawRequestMapper;
import io.github.open55.otx.infrastructure.po.WithdrawRequestPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 提现请求聚合根仓储实现。
 * <p>
 * 使用 MyBatis-Plus 的 BaseMapper 实现持久化，通过 WithdrawRequestConverter 完成 PO ↔ Entity 互转。
 */
@Repository
public class WithdrawRequestRepoImpl implements WithdrawRequestRepo {

    @Resource
    private WithdrawRequestMapper withdrawRequestMapper;

    /**
     * 持久化新的提现请求。
     * <p>
     * 将 Entity 转换为 PO 后调用 insert 写入 withdraw_request_t 表。
     *
     * @param entity 待持久化的提现请求聚合根
     */
    @Override
    public void save(WithdrawRequestEntity entity) {
        WithdrawRequestPO po = WithdrawRequestConverter.INSTANCE.entity2po(entity);
        withdrawRequestMapper.insert(po);
        // MyBatis-Plus 将生成的主键回写到 PO，需要同步回 Entity
        if (entity.getId() == null) {
            entity.setId(po.getId());
        }
    }

    /**
     * 按业务流水号查询提现请求。
     * <p>
     * 查询 withdraw_request_t 表中 biz_no 匹配的记录，转换为 Entity 返回。
     * 查不到时返回 Optional.empty()。
     *
     * @param bizNo 业务流水号
     * @return 提现请求聚合根，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<WithdrawRequestEntity> findByBizNo(String bizNo) {
        WithdrawRequestPO po = withdrawRequestMapper.selectOne(
                new LambdaQueryWrapper<WithdrawRequestPO>()
                        .eq(WithdrawRequestPO::getBizNo, bizNo));
        return Optional.ofNullable(WithdrawRequestConverter.INSTANCE.po2Entity(po));
    }

    /**
     * 判断指定业务流水号是否已存在。
     * <p>
     * 使用 selectCount 统计匹配 biz_no 的记录数，大于 0 表示已存在。
     *
     * @param bizNo 业务流水号
     * @return true 表示已存在
     */
    @Override
    public boolean existsByBizNo(String bizNo) {
        return withdrawRequestMapper.selectCount(
                new LambdaQueryWrapper<WithdrawRequestPO>()
                        .eq(WithdrawRequestPO::getBizNo, bizNo)) > 0;
    }

    /**
     * 按用户唯一标识查询该用户的全部提现请求，按创建时间降序返回（最新在前）。
     *
     * @param uid 用户唯一标识
     * @return 提现请求列表
     */
    @Override
    public List<WithdrawRequestEntity> findByUid(Long uid) {
        List<WithdrawRequestPO> poList = withdrawRequestMapper.selectList(
                new LambdaQueryWrapper<WithdrawRequestPO>()
                        .eq(WithdrawRequestPO::getUid, uid)
                        .orderByDesc(WithdrawRequestPO::getCreateTime));
        return WithdrawRequestConverter.INSTANCE.po2EntityList(poList);
    }

    /**
     * 查询全部提现请求，按创建时间降序返回（最新在前）。
     * <p>
     * 管理控制台提现单据列表的数据源，全量返回，数据量大时由上层引入分页。
     *
     * @return 全部提现请求列表
     */
    @Override
    public List<WithdrawRequestEntity> findAll() {
        List<WithdrawRequestPO> poList = withdrawRequestMapper.selectList(
                new LambdaQueryWrapper<WithdrawRequestPO>()
                        .orderByDesc(WithdrawRequestPO::getCreateTime));
        return WithdrawRequestConverter.INSTANCE.po2EntityList(poList);
    }

    /**
     * 更新提现请求（如广播后状态与交易哈希变更、结算后状态变更）。
     * <p>
     * 将 Entity 转换为 PO 后调用 updateById 更新 withdraw_request_t 表中对应行。
     *
     * @param entity 待更新的提现请求聚合根
     */
    @Override
    public void update(WithdrawRequestEntity entity) {
        WithdrawRequestPO po = WithdrawRequestConverter.INSTANCE.entity2po(entity);
        withdrawRequestMapper.updateById(po);
    }
}
