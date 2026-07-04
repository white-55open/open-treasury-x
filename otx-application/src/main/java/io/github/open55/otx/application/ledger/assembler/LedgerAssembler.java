package io.github.open55.otx.application.ledger.assembler;

import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Ledger 模块 DTO 与 Entity 之间的双向转换器。
 * <p>
 * 单向映射：请求 → 实体、实体 → 响应，不实现反向。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface LedgerAssembler {

    LedgerAssembler INSTANCE = Mappers.getMapper(LedgerAssembler.class);

    /**
     * PostJournalRequestDTO → LedgerJournalEntity（通过工厂方法创建）
     * <p>
     * 请求中的 LedgerEntryRequestDTO 列表先转换为 LedgerEntryEntity 列表，
     * 再通过 {@link LedgerJournalEntity#create} 工厂方法构造聚合根。
     */
    default LedgerJournalEntity toEntity(PostJournalRequestDTO req) {
        List<LedgerEntryEntity> entries = toEntryEntities(req.getEntries());
        return LedgerJournalEntity.create(
                req.getBizNo(), req.getBizType(), req.getCurrency(),
                req.getPostingDate(), req.getDescription(), entries,
                req.getChainId(), req.getChainTxHash(),
                req.getBlockNumber(), req.getTokenAddress()
        );
    }

    /**
     * LedgerEntryRequestDTO → LedgerEntryEntity
     * <p>
     * 将请求中的分录规格转换领域值对象，字段一一对应。
     */
    default LedgerEntryEntity toEntryEntity(LedgerEntryRequestDTO spec) {
        return new LedgerEntryEntity(
                spec.getAccountCode(), spec.getEntryType(), spec.getAmount(),
                spec.getUid(), spec.getCounterparty(),
                spec.getBalanceAfter(), spec.getRemark()
        );
    }

    /**
     * List<LedgerEntryRequestDTO> → List<LedgerEntryEntity>
     */
    List<LedgerEntryEntity> toEntryEntities(List<LedgerEntryRequestDTO> specs);

    /**
     * LedgerJournalEntity → JournalDetailResponseDTO
     * <p>
     * 实体中的枚举字段（bizType/status）转换为字符串，entries 列表递归转换。
     */
    JournalDetailResponseDTO toResponse(LedgerJournalEntity entity);

    /**
     * LedgerEntryEntity → LedgerEntryResponseDTO
     * <p>
     * 枚举字段（accountCode/entryType）通过 name() 转换为字符串。
     */
    LedgerEntryResponseDTO toEntryResponse(LedgerEntryEntity entry);

    /**
     * List<LedgerEntryEntity> → List<LedgerEntryResponseDTO>
     */
    List<LedgerEntryResponseDTO> toEntryResponses(List<LedgerEntryEntity> entries);
}
