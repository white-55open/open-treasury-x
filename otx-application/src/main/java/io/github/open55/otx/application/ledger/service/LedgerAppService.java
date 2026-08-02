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

    /**
     * 创建草稿凭证：保存凭证与分录但不执行过账，状态保持 DRAFT。
     * <p>
     * 用于链上广播编排的留痕：广播成功后先落 DRAFT 凭证（携带链上字段），
     * 待链上确认达标后再通过 {@link #postJournalByBizNo(String)} 过账。
     * 幂等保证与 {@link #postJournal(PostJournalRequestDTO)} 一致：
     * 相同 bizNo 的重复请求返回已存在的 DRAFT 凭证，不产生副作用。
     *
     * @param req 过账请求，借贷必须平衡（保存前校验）
     * @return 凭证详情，status 为 DRAFT
     * @throws io.github.open55.otx.common.exception.BizException 借贷不平衡时抛 LEDGER_NOT_BALANCED
     */
    JournalDetailResponseDTO createDraftJournal(PostJournalRequestDTO req);

    /**
     * 按业务流水号过账草稿凭证：将 DRAFT 状态推进为 POSTED。
     * <p>
     * 幂等保证：已 POSTED 的凭证直接返回已有结果；REVERSED 或已过账凭证
     * 抛 LEDGER_JOURNAL_NOT_DRAFT；凭证不存在抛 LEDGER_JOURNAL_NOT_FOUND。
     *
     * @param bizNo 业务流水号
     * @return 凭证详情，status 为 POSTED
     * @throws io.github.open55.otx.common.exception.BizException 凭证不存在抛 LEDGER_JOURNAL_NOT_FOUND；
     *                                                          状态非 DRAFT 抛 LEDGER_JOURNAL_NOT_DRAFT
     */
    JournalDetailResponseDTO postJournalByBizNo(String bizNo);
}
