package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.infrastructure.po.LedgerJournalPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 凭证聚合根 PO ↔ Entity 转换器。
 * <p>
 * 枚举字段（bizType / status）在 PO 中用 String，Entity 中用 Enum，MapStruct 自动完成 String ↔ Enum 互转。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface LedgerJournalConverter {

    LedgerJournalConverter INSTANCE = Mappers.getMapper(LedgerJournalConverter.class);

    /**
     * PO → Entity，将数据库行转换为领域实体
     */
    LedgerJournalEntity po2Entity(LedgerJournalPO po);

    /**
     * PO 列表 → Entity 列表
     */
    List<LedgerJournalEntity> po2EntityList(List<LedgerJournalPO> poList);

    /**
     * Entity → PO，将领域实体转换为数据库行
     */
    LedgerJournalPO entity2po(LedgerJournalEntity entity);

    /**
     * Entity 列表 → PO 列表
     */
    List<LedgerJournalPO> entity2poList(List<LedgerJournalEntity> entityList);
}
