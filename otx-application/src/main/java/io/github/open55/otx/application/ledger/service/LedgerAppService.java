package io.github.open55.otx.application.ledger.service;

import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;

/**
 * Ledger 应用服务入站端口，定义过账与查询两个用例。
 */
public interface LedgerAppService {

    /**
     * 过账：创建并过账一张凭证。
     * <p>
     * 幂等保证：相同 bizNo 的重复请求返回已存在的 Journal，不产生副作用。
     *
     * @param req 过账请求，包含业务流水号、业务类型、币种、分录列表等信息
     * @return 凭证详情，包含 Journal 及其全部 Entry
     */
    JournalDetailResponseDTO postJournal(PostJournalRequestDTO req);

    /**
     * 按业务流水号查询凭证详情。
     *
     * @param bizNo 业务流水号
     * @return 凭证详情，包含 Journal 及其全部 Entry
     * @throws io.github.open55.otx.common.exception.BizException bizNo 不存在时抛 LEDGER_JOURNAL_NOT_FOUND
     */
    JournalDetailResponseDTO findByBizNo(String bizNo);
}
