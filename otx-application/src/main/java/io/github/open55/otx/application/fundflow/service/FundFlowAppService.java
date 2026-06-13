package io.github.open55.otx.application.fundflow.service;

import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;

import java.util.List;

public interface FundFlowAppService {
    void record(CreateFundFlowRequest input);

    boolean existsBizNo(String bizNo);

    List<FundFlowEntity> findByUid(Long uid);
}
