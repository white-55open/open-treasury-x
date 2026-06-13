package io.github.open55.otx.application.fundflow.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.common.util.SnowflakeIdUtil;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.repository.FundFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FundFlowAppServiceImpl implements FundFlowAppService {
    private final FundFlowRepository repository;

    @Override
    public void record(CreateFundFlowRequest input) {
        Assert.notNull(input);
        Assert.notNull(input.getUid());
        Assert.isTrue(input.getUid() > 0);
        Assert.notBlank(input.getBizNo());
        Assert.notNull(input.getAmount());
        Assert.notNull(input.getBalanceBefore());
        Assert.notNull(input.getBalanceAfter());
        Assert.notNull(input.getDirection());
        Assert.notNull(input.getType());

        FundFlowEntity flow = new FundFlowEntity();
        flow.setFlowNo(String.valueOf(SnowflakeIdUtil.nextId()));
        flow.setUid(input.getUid());
        flow.setBizNo(input.getBizNo());
        flow.setAmount(input.getAmount());
        flow.setBalanceBefore(input.getBalanceBefore());
        flow.setBalanceAfter(input.getBalanceAfter());
        flow.setDirection(input.getDirection());
        flow.setType(input.getType());

        repository.save(flow);
    }

    @Override
    public boolean existsBizNo(String bizNo) {
        Assert.notBlank(bizNo);
        return repository.existsByBizNo(bizNo);
    }

    @Override
    public List<FundFlowEntity> findByUid(Long uid) {
        Assert.notNull(uid);
        Assert.isTrue(uid > 0);
        return repository.findByUid(uid);
    }
}
