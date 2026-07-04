package io.github.open55.otx.application.fundflow.service;

import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;

import java.util.List;

/**
 * 资金流水应用服务接口，定义流水记录的用例边界。
 */
public interface FundFlowAppService {
    /**
     * 记录一笔资金流水。
     *
     * @param input 资金流水请求
     */
    void record(CreateFundFlowRequest input);

    /**
     * 按业务流水号检查流水是否已存在。
     *
     * @param bizNo 业务流水号
     * @return 存在返回 true
     */
    boolean existsBizNo(String bizNo);

    /**
     * 按用户标识查询所有资金流水。
     *
     * @param uid 用户唯一标识
     * @return 资金流水列表
     */
    List<FundFlowEntity> findByUid(Long uid);
}
