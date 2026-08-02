package io.github.open55.otx.domain.withdraw;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 提现请求聚合根（Aggregate Root）。
 * <p>
 * 承载提现广播编排的链上状态：目标地址、代币地址、交易哈希与状态机
 * （PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED）。
 * 所有状态变更必须通过领域方法执行，非法转移抛 WITHDRAW_REQUEST_STATUS_INVALID。
 * 作为聚合的唯一入口，仓储只操作该聚合根。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WithdrawRequestEntity extends BaseEntity {

    /**
     * 用户唯一标识
     * <p>提现发起人，作为查询与风控的业务键</p>
     */
    private Long uid;

    /**
     * 业务流水号，用作幂等键，全局唯一
     * <p>贯穿冻结 → 广播 → 结算全流程，由 uk_biz_no 唯一索引兜底</p>
     */
    private String bizNo;

    /**
     * 提现金额
     * <p>必须为正数，与冻结金额一致，结算时按此金额扣减冻结余额</p>
     */
    private BigDecimal amount;

    /**
     * 币种
     * <p>如 USDT、ETH，与账户币种一致</p>
     */
    private String currency;

    /**
     * 区块链 ID
     * <p>如 "1"（以太坊主网）、"11155111"（Sepolia 测试网）</p>
     */
    private String chainId;

    /**
     * 接收方地址
     * <p>用户提现的目标链上地址</p>
     */
    private String toAddress;

    /**
     * 代币合约地址
     * <p>ERC-20 代币提现时必填；原生币提现时为空</p>
     */
    private String tokenAddress;

    /**
     * 链上交易哈希
     * <p>广播成功后回填，用于确认结算与审计；未广播时为空</p>
     */
    private String txHash;

    /**
     * 提现请求状态
     * <p>初始为 PENDING，由状态机领域方法驱动流转</p>
     */
    private WithdrawRequestStatusEnum status = WithdrawRequestStatusEnum.PENDING;

    /**
     * 工厂方法，创建一条新的提现请求。
     * <p>
     * 执行入参校验后才构造实例，确保聚合根始终有效。
     *
     * @param uid          用户唯一标识，不可为空
     * @param bizNo        业务流水号，不可为空
     * @param amount       提现金额，不可为空且必须为正数
     * @param currency     币种，不可为空
     * @param chainId      区块链 ID，不可为空
     * @param toAddress    接收方地址，不可为空
     * @param tokenAddress 代币合约地址（可选）
     * @return 新建的提现请求实例，状态为 PENDING
     */
    public static WithdrawRequestEntity create(Long uid, String bizNo, BigDecimal amount,
                                               String currency, String chainId,
                                               String toAddress, String tokenAddress) {
        // 校验业务流水号：幂等键必填，不允许空白
        if (bizNo == null || bizNo.isBlank()) {
            throw BizException.get(BizErrorEnum.BIZ_NO_EMPTY);
        }
        // 校验用户标识：必填
        if (uid == null) {
            throw BizException.get(BizErrorEnum.UID_CANT_NULL);
        }
        // 校验金额：必填，不允许为空
        if (amount == null) {
            throw BizException.get(BizErrorEnum.AMOUNT_CANT_NULL);
        }
        // 校验金额：必须为正数（零或负金额的提现请求非法）
        if (amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.WITHDRAW_AMOUNT_INVALID);
        }
        // 校验币种：必填，不允许空白
        if (currency == null || currency.isBlank()) {
            throw BizException.get(BizErrorEnum.PARAM_MISS);
        }
        // 校验区块链 ID：必填，不允许空白
        if (chainId == null || chainId.isBlank()) {
            throw BizException.get(BizErrorEnum.PARAM_MISS);
        }
        // 校验接收方地址：必填，不允许空白
        if (toAddress == null || toAddress.isBlank()) {
            throw BizException.get(BizErrorEnum.PARAM_MISS);
        }
        return WithdrawRequestEntity.builder()
                .uid(uid)
                .bizNo(bizNo)
                .amount(amount)
                .currency(currency)
                .chainId(chainId)
                .toAddress(toAddress)
                .tokenAddress(tokenAddress)
                .status(WithdrawRequestStatusEnum.PENDING)
                .build();
    }

    /**
     * 标记已广播：记录链上交易哈希并将状态置为 BROADCASTED。
     * <p>
     * 允许的状态转移：PENDING → BROADCASTED、FAILED → BROADCASTED（失败重试）。
     * 广播成功后不可重复广播，SETTLED / CANCELLED 状态调用抛 WITHDRAW_REQUEST_STATUS_INVALID。
     *
     * @param txHash 链上交易哈希，不可为空或空白
     * @throws BizException            非法状态转移时抛 WITHDRAW_REQUEST_STATUS_INVALID
     * @throws IllegalArgumentException txHash 为空或空白时抛出
     */
    public void markBroadcasted(String txHash) {
        // 校验交易哈希：必填，不允许空白（无哈希的广播记录无法确认结算）
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalArgumentException("txHash must not be null or blank");
        }
        // 仅 PENDING / FAILED 状态允许进入 BROADCASTED（FAILED 为广播重试路径）
        if (status != WithdrawRequestStatusEnum.PENDING
                && status != WithdrawRequestStatusEnum.FAILED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }
        this.txHash = txHash;
        this.status = WithdrawRequestStatusEnum.BROADCASTED;
    }

    /**
     * 标记已结算：将状态置为 SETTLED。
     * <p>
     * 允许的状态转移：BROADCASTED → SETTLED（链上确认达标后由结算用例调用）。
     * 其他状态调用抛 WITHDRAW_REQUEST_STATUS_INVALID。
     *
     * @throws BizException 非法状态转移时抛 WITHDRAW_REQUEST_STATUS_INVALID
     */
    public void markSettled() {
        // 仅 BROADCASTED 状态允许进入 SETTLED（必须先广播取得交易哈希）
        if (status != WithdrawRequestStatusEnum.BROADCASTED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }
        this.status = WithdrawRequestStatusEnum.SETTLED;
    }

    /**
     * 标记失败：将状态置为 FAILED。
     * <p>
     * 允许的状态转移：PENDING → FAILED（签名/广播失败）、BROADCASTED → FAILED（链上交易失败）。
     * SETTLED / CANCELLED 终态调用抛 WITHDRAW_REQUEST_STATUS_INVALID。
     *
     * @throws BizException 非法状态转移时抛 WITHDRAW_REQUEST_STATUS_INVALID
     */
    public void markFailed() {
        // 仅 PENDING / BROADCASTED 状态允许进入 FAILED
        if (status != WithdrawRequestStatusEnum.PENDING
                && status != WithdrawRequestStatusEnum.BROADCASTED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }
        this.status = WithdrawRequestStatusEnum.FAILED;
    }

    /**
     * 取消提现：将状态置为 CANCELLED。
     * <p>
     * 允许的状态转移：PENDING → CANCELLED、BROADCASTED → CANCELLED、FAILED → CANCELLED
     * （取消路径由应用层先解冻资金再调用本方法）。
     * 已结算的请求不可取消，调用抛 WITHDRAW_REQUEST_STATUS_INVALID。
     *
     * @throws BizException 非法状态转移时抛 WITHDRAW_REQUEST_STATUS_INVALID
     */
    public void cancel() {
        // 仅 PENDING / BROADCASTED / FAILED 状态允许取消；SETTLED 为终态不可取消
        if (status != WithdrawRequestStatusEnum.PENDING
                && status != WithdrawRequestStatusEnum.BROADCASTED
                && status != WithdrawRequestStatusEnum.FAILED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }
        this.status = WithdrawRequestStatusEnum.CANCELLED;
    }
}
