package io.github.open55.otx.application.fundflow.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.common.util.SnowflakeIdUtil;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.repository.FundFlowRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资金流水应用服务实现。
 * <p>
 * 负责创建资金流水记录（含雪花 ID 生成）以及流水查询与幂等校验。
 */
@Service
@RequiredArgsConstructor
public class FundFlowAppServiceImpl implements FundFlowAppService {
    private final FundFlowRepo repository;

    /**
     * 记录一笔资金流水，使用雪花算法生成唯一流水号。
     *
     * @param input 资金流水请求，必须包含 uid、bizNo、金额及余额快照
     */
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

    /**
     * 按业务流水号检查流水是否存在（幂等校验）。
     *
     * @param bizNo 业务流水号
     * @return 存在返回 true
     */
    @Override
    public boolean existsBizNo(String bizNo) {
        Assert.notBlank(bizNo);
        return repository.existsByBizNo(bizNo);
    }

    /**
     * 按用户标识查询所有资金流水。
     *
     * @param uid 用户唯一标识
     * @return 资金流水列表
     */
    @Override
    public List<FundFlowEntity> findByUid(Long uid) {
        Assert.notNull(uid);
        Assert.isTrue(uid > 0);
        return repository.findByUid(uid);
    }
}
