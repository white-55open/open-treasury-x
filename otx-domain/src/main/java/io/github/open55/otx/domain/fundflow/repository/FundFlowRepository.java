package io.github.open55.otx.domain.fundflow.repository;

import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;

import java.util.List;

public interface FundFlowRepository {

    void save(FundFlowEntity fundFlow);

    boolean existsByBizNo(String bizNo);

    List<FundFlowEntity> findByUid(Long uid);
}
