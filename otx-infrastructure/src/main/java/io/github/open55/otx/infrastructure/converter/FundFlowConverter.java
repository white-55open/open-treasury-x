package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(builder = @Builder(disableBuilder = true))
public interface FundFlowConverter {
    FundFlowConverter INSTANCE = Mappers.getMapper(FundFlowConverter.class);

    FundFlowEntity po2Entity(FundFlowPO po);

    List<FundFlowEntity> po2EntityList(List<FundFlowPO> poList);

    FundFlowPO entity2po(FundFlowEntity entity);
}
