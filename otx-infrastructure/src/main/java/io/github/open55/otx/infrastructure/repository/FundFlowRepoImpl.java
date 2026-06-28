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

@Repository
@RequiredArgsConstructor
public class FundFlowRepoImpl implements FundFlowRepo {
    private final FundFlowMapper fundFlowMapper;

    @Override
    public void save(FundFlowEntity fundFlow) {
        Assert.notNull(fundFlow);
        FundFlowPO po = FundFlowConverter.INSTANCE.entity2po(fundFlow);
        fundFlowMapper.insert(po);
    }

    @Override
    public boolean existsByBizNo(String bizNo) {
        Assert.notBlank(bizNo);
        return fundFlowMapper.exists(new LambdaQueryWrapper<FundFlowPO>()
                .eq(FundFlowPO::getBizNo, bizNo));
    }

    @Override
    public List<FundFlowEntity> findByUid(Long uid) {
        Assert.notNull(uid);
        Assert.isTrue(uid > 0);
        List<FundFlowPO> poList = fundFlowMapper.selectList(new LambdaQueryWrapper<FundFlowPO>()
                .eq(FundFlowPO::getUid, uid));
        return FundFlowConverter.INSTANCE.po2EntityList(poList);
    }
}
