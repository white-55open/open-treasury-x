package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.infrastructure.po.LedgerEntryPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 分录值对象 PO ↔ Entity 转换器。
 * <p>
 * 枚举字段（accountCode / entryType）在 PO 中用 String，Entity 中用 Enum，MapStruct 自动完成 String ↔ Enum 互转。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface LedgerEntryConverter {

    LedgerEntryConverter INSTANCE = Mappers.getMapper(LedgerEntryConverter.class);

    /**
     * PO → Entity，将数据库行转换为领域值对象
     */
    LedgerEntryEntity po2Entity(LedgerEntryPO po);

    /**
     * PO 列表 → Entity 列表
     */
    List<LedgerEntryEntity> po2EntityList(List<LedgerEntryPO> poList);

    /**
     * Entity → PO，将领域值对象转换为数据库行
     * <p>
     * journalId 和 bizNo 由仓储层在持久化前通过 PO setter 直接设置，不属于领域值对象的业务字段
     */
    @Mapping(target = "journalId", ignore = true)
    @Mapping(target = "bizNo", ignore = true)
    LedgerEntryPO entity2po(LedgerEntryEntity entity);

    /**
     * Entity 列表 → PO 列表
     */
    List<LedgerEntryPO> entity2poList(List<LedgerEntryEntity> entityList);
}
