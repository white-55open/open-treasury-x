package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.infrastructure.po.WithdrawRequestPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 提现请求聚合根 PO ↔ Entity 转换器。
 * <p>
 * 枚举字段（status）在 PO 中用 String，Entity 中用 Enum，MapStruct 自动完成 String ↔ Enum 互转。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface WithdrawRequestConverter {

    WithdrawRequestConverter INSTANCE = Mappers.getMapper(WithdrawRequestConverter.class);

    /**
     * PO → Entity，将数据库行转换为领域实体，status 从状态码 String 转为 WithdrawRequestStatusEnum
     */
    WithdrawRequestEntity po2Entity(WithdrawRequestPO po);

    /**
     * PO 列表 → Entity 列表，批量转换数据库行
     */
    List<WithdrawRequestEntity> po2EntityList(List<WithdrawRequestPO> poList);

    /**
     * Entity → PO，将领域实体转换为数据库行，status 从 WithdrawRequestStatusEnum 转为状态码 String
     */
    WithdrawRequestPO entity2po(WithdrawRequestEntity entity);

    /**
     * Entity 列表 → PO 列表，批量转换领域实体
     */
    List<WithdrawRequestPO> entity2poList(List<WithdrawRequestEntity> entityList);
}
