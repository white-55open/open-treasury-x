package io.github.open55.otx.application.account.service;

import io.github.open55.otx.application.account.dto.response.AccountSummaryDTO;
import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户应用服务接口，定义账户管理的用例边界。
 */
public interface AccountAppService {

    /**
     * 为用户创建账户，若已存在则直接返回现有账户标识。
     *
     * @param uid 用户唯一标识
     * @return 账户标识（主键 ID）
     */
    Long createAccount(Long uid);

    /**
     * 增加用户可用余额（直接加记，无冻结校验）。
     *
     * @param uid    用户唯一标识
     * @param amount 增加金额
     */
    void increaseBalance(Long uid, BigDecimal amount);

    /**
     * 冻结用户指定金额。
     *
     * @param uid    用户唯一标识
     * @param amount 冻结金额
     */
    void freezeBalance(Long uid, BigDecimal amount);

    /**
     * 按用户标识查询账户详情。
     *
     * @param uid 用户唯一标识
     * @return 账户详情响应
     */
    GetAccountResponse getByUid(Long uid);

    /**
     * 查询全部账户摘要，按创建时间升序返回。
     * <p>
     * 只读查询，数据量大时后续引入分页；管理控制台账户总览的数据源。
     *
     * @return 账户摘要列表
     */
    List<AccountSummaryDTO> listAccounts();

    /**
     * 变更金额并记录资金流水（含幂等处理）。
     * <p>
     * 仅支持充值（DEPOSIT）资金变更类型，WITHDRAW 及其他类型会抛出
     * FUND_FLOW_TYPE_NOT_SUPPORT（提现已由 withdraw 上下文的两阶段
     * 冻结-结算-解冻用例承担，不再复用本方法）。
     * 通过业务流水号（bizNo）实现幂等，重复请求返回相同结果。
     *
     * @param request 资金变更请求
     * @return 业务流水号
     */
    String changeAmountWithFundFlow(ChangeAmountRequest request);
}
