package io.github.open55.otx.infrastructure.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.repository.FundFlowRepo;
import io.github.open55.otx.infrastructure.converter.FundFlowConverter;
import io.github.open55.otx.infrastructure.mapper.FundFlowMapper;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 资金流水仓储实现，通过 MyBatis-Plus 完成流水持久化操作。
 */
@Repository
@RequiredArgsConstructor
public class FundFlowRepoImpl implements FundFlowRepo {
    private final FundFlowMapper fundFlowMapper;

    /**
     * 持久化资金流水，Entity 转 PO 后插入。
     *
     * @param fundFlow 资金流水实体
     */
    @Override
    public void save(FundFlowEntity fundFlow) {
        Assert.notNull(fundFlow);
        FundFlowPO po = FundFlowConverter.INSTANCE.entity2po(fundFlow);
        fundFlowMapper.insert(po);
    }

    /**
     * 按业务流水号检查流水是否存在（基于 biz_no 唯一索引）。
     *
     * @param bizNo 业务流水号
     * @return 存在返回 true
     */
    @Override
    public boolean existsByBizNo(String bizNo) {
        Assert.notBlank(bizNo);
        return fundFlowMapper.exists(new LambdaQueryWrapper<FundFlowPO>()
                .eq(FundFlowPO::getBizNo, bizNo));
    }

    /**
     * 按用户标识查询所有资金流水，PO 转 Entity 后返回。
     *
     * @param uid 用户唯一标识
     * @return 资金流水列表
     */
    @Override
    public List<FundFlowEntity> findByUid(Long uid) {
        Assert.notNull(uid);
        Assert.isTrue(uid > 0);
        List<FundFlowPO> poList = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlowPO>()
                .eq(FundFlowPO::getUid, uid));
        return FundFlowConverter.INSTANCE.po2EntityList(poList);
    }
}
