package io.github.open55.otx.domain.fundflow.repository;

import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;

import java.util.List;

/**
 * 资金流水仓储接口，定义流水持久化的契约。
 * <p>
 * 接口定义在领域层（出站端口），实现在基础设施层。
 */
public interface FundFlowRepo {

    /**
     * 持久化资金流水记录。
     *
     * @param fundFlow 资金流水实体
     */
    void save(FundFlowEntity fundFlow);

    /**
     * 按业务流水号检查流水是否已存在（幂等校验）。
     *
     * @param bizNo 业务流水号
     * @return 存在返回 true
     */
    boolean existsByBizNo(String bizNo);

    /**
     * 按用户标识查询所有资金流水。
     *
     * @param uid 用户唯一标识
     * @return 资金流水列表
     */
    List<FundFlowEntity> findByUid(Long uid);
}
