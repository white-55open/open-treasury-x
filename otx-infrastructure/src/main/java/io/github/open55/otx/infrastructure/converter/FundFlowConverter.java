package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * FundFlowPO 与 FundFlowEntity 之间的双向转换器。
 * <p>
 * 枚举字段 direction（String ↔ FundFlowDirectionEnum）和 type（String ↔ FundFlowTypeEnum）
 * 由 MapStruct 自动完成映射。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface FundFlowConverter {
    FundFlowConverter INSTANCE = Mappers.getMapper(FundFlowConverter.class);

    /**
     * FundFlowPO → FundFlowEntity，同名字段自动映射，枚举字段自动转换。
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    FundFlowEntity po2Entity(FundFlowPO po);

    /**
     * FundFlowPO 列表 → FundFlowEntity 列表。
     *
     * @param poList 持久化对象列表
     * @return 领域实体列表
     */
    List<FundFlowEntity> po2EntityList(List<FundFlowPO> poList);

    /**
     * FundFlowEntity → FundFlowPO，枚举字段自动转为 String。
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    FundFlowPO entity2po(FundFlowEntity entity);
}
