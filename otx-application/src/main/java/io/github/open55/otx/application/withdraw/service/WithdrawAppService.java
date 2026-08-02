package io.github.open55.otx.application.withdraw.service;

import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;

/**
 * 提现应用服务接口，定义两阶段提现与链上广播编排的用例边界。
 * <p>
 * 冻结、结算、解冻三个链下账务用例携带独立业务号（bizNo）作为幂等键，
 * 返回值统一为业务流水号；广播、确认结算、取消与状态查询四个链上用例
 * 以提现请求记录（WithdrawRequestEntity）为状态载体，返回结构化响应。
 */
public interface WithdrawAppService {

    /**
     * 冻结用例：将申请金额从可用余额转入冻结余额。
     * <p>
     * 业务场景：用户提交提现申请时锁定资金，防止审核期间重复消费。
     * 资金效果为可用余额减少、冻结余额增加，同时落 FREEZE 流水并过账
     * 冻结凭证（借记用户可用余额，贷记用户冻结余额）。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    String freeze(WithdrawRequestDTO request);

    /**
     * 结算用例：从冻结余额扣减指定金额，资金进入提现在途。
     * <p>
     * 业务场景：提现申请审核通过后完成最终结算。
     * 仅校验冻结余额充足性，资金效果为冻结余额减少、可用余额不变，
     * 同时落 WITHDRAW 流水并过账结算凭证（借记用户冻结余额，贷记提现在途）。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    String withdraw(WithdrawRequestDTO request);

    /**
     * 解冻用例：将冻结余额退回可用余额。
     * <p>
     * 业务场景：提现申请取消或审核驳回时释放已冻结资金。
     * 资金效果为冻结余额减少、可用余额增加，同时落 UNFREEZE 流水并过账
     * 解冻凭证（借记用户冻结余额，贷记用户可用余额），不冲销原冻结凭证。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    String unfreeze(WithdrawRequestDTO request);

    /**
     * 广播用例：构造交易、签名并提交链上，为提现请求留痕。
     * <p>
     * 业务流程：保存 PENDING 提现请求 → 事务外查询 nonce、签名、广播 →
     * 广播成功置 BROADCASTED 并创建 DRAFT 凭证（携带链上字段）；
     * 广播失败置 FAILED 且不创建凭证。幂等保证：相同 bizNo 重复请求
     * 返回已有请求的广播结果。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency、chainId、toAddress
     * @return 广播结果，包含 bizNo、txHash 与请求状态
     * @throws io.github.open55.otx.common.exception.BizException 签名失败抛 TX_SIGN_FAILED；
     *                                                          广播失败抛 TX_BROADCAST_FAILED
     */
    WithdrawBroadcastResponseDTO broadcast(WithdrawRequestDTO request);

    /**
     * 确认结算用例：链上确认达标后完成冻结资金扣减与凭证过账。
     * <p>
     * 业务流程：加载提现请求（SETTLED 幂等返回）→ 查询链上回执判断交易
     * 是否存在与成功 → 确认数达标后扣减冻结余额、落 WITHDRAW 流水并
     * 置 SETTLED，随后过账 DRAFT 凭证；未确认抛 TX_NOT_CONFIRMED_YET；
     * 链上交易失败或缺失则解冻资金、置 FAILED 并抛 TX_CHAIN_FAILED。
     *
     * @param bizNo 业务流水号
     * @return 结算结果，包含 bizNo、请求状态与凭证状态
     * @throws io.github.open55.otx.common.exception.BizException 请求不存在抛 WITHDRAW_REQUEST_NOT_FOUND；
     *                                                          未确认抛 TX_NOT_CONFIRMED_YET；
     *                                                          链上失败抛 TX_CHAIN_FAILED
     */
    WithdrawSettleResponseDTO confirmAndSettle(String bizNo);

    /**
     * 取消用例：解冻已冻结资金并置请求为 CANCELLED。
     * <p>
     * 业务场景：风控拒绝、审核不通过或用户主动取消。
     * 解冻资金并落 UNFREEZE 流水；已取消请求幂等返回，已结算请求不可取消。
     *
     * @param bizNo 业务流水号
     * @return 取消后的提现请求状态
     * @throws io.github.open55.otx.common.exception.BizException 请求不存在抛 WITHDRAW_REQUEST_NOT_FOUND；
     *                                                          已结算抛 WITHDRAW_REQUEST_STATUS_INVALID
     */
    WithdrawStatusResponseDTO cancelWithdraw(String bizNo);

    /**
     * 状态查询用例：按业务流水号查询提现请求的当前状态。
     *
     * @param bizNo 业务流水号
     * @return 提现请求状态，包含 bizNo、status、txHash、amount、currency
     * @throws io.github.open55.otx.common.exception.BizException 请求不存在抛 WITHDRAW_REQUEST_NOT_FOUND
     */
    WithdrawStatusResponseDTO queryStatus(String bizNo);
}
