package io.github.open55.otx.application.withdraw.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.SignerPort;
import io.github.open55.otx.domain.chain.port.TxBroadcastPort;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.domain.withdraw.WithdrawRequestRepo;
import io.github.open55.otx.domain.withdraw.WithdrawRequestStatusEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 提现应用服务实现，编排两阶段提现的三个链下用例与链上广播编排四个用例。
 * <p>
 * 冻结（freeze）、结算（withdraw）、解冻（unfreeze）各自独立事务，
 * 均遵循"余额变更 + 流水记录强一致、总账过账最终一致"的既有范式：
 * 入口方法完成参数校验与幂等前置检查，通过 self 代理调用原子方法触发
 * Spring AOP；原子方法在 REQUIRES_NEW 事务内完成动账与落流水，
 * 事务提交后过账总账，过账失败仅记录 WARN 日志，由对账模块后续修复。
 * <p>
 * 广播（broadcast）、确认结算（confirmAndSettle）、取消（cancelWithdraw）、
 * 状态查询（queryStatus）以提现请求记录为状态载体：外部 IO（nonce 查询、
 * 签名、广播、链上回执查询）一律在事务外执行，账务与状态变更走独立原子
 * 事务（REQUIRES_NEW + 乐观锁重试），遵循 design D8 事务边界。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawAppServiceImpl implements WithdrawAppService {

    private final AccountRepo accountRepo;

    private final FundFlowAppService fundFlowAppService;

    private final LedgerAppService ledgerAppService;

    private final WithdrawRequestRepo withdrawRequestRepo;

    private final SignerPort signerPort;

    private final TxBroadcastPort txBroadcastPort;

    private final ChainQueryPort chainQueryPort;

    /**
     * 结算所需的链上确认数，经配置注入。
     * <p>
     * 读取 otx.chain-tx.required-confirmations 配置（缺省 12），
     * 通过 @Value 读取配置不违反依赖铁律（application 不依赖 infrastructure 的类）。
     * 非 final 字段，避免进入 @RequiredArgsConstructor 生成的构造器。
     */
    @Value("${otx.chain-tx.required-confirmations:12}")
    private int requiredConfirmations;

    /**
     * 平台热钱包地址（交易发送方），经配置注入。
     * <p>
     * 本变更无钱包管理模块，广播编排构造签名请求与查询 nonce 时需要
     * 发送方地址；配置缺失时为空串，由签名适配器（本地 keystore 从
     * keystore 推导地址）与链上查询兜底处理。
     */
    @Value("${otx.chain-tx.hot-wallet-address:}")
    private String fromAddress;

    @Resource
    @Lazy
    private WithdrawAppServiceImpl self;

    /**
     * 冻结用例：将申请金额从可用余额转入冻结余额。
     * <p>
     * 入口方法：参数校验 → bizNo 幂等前置检查 → 自代理调用冻结原子方法
     * （REQUIRES_NEW 事务内动账 + 落 FREEZE 流水），捕获唯一键冲突视为
     * 幂等成功，事务提交后过账冻结凭证，过账失败仅 WARN 不回滚。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    @Override
    public String freeze(WithdrawRequestDTO request) {
        validateCommon(request);
        Assert.isTrue(request.getAmount().signum() > 0,
                () -> BizException.get(BizErrorEnum.FREEZE_AMOUNT_INVALID));

        // 幂等前置检查：相同 bizNo 已处理过则直接返回
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            self.freezeAtomic(request);
        } catch (BizIdempotentException e) {
            // 唯一键冲突说明流水已存在，视为幂等成功
        }

        postJournalSafely(buildFreezeJournalRequest(request), request.getBizNo());
        return request.getBizNo();
    }

    /**
     * 结算用例：从冻结余额扣减指定金额，资金进入提现在途。
     * <p>
     * 入口方法：参数校验 → bizNo 幂等前置检查 → 自代理调用结算原子方法
     * （REQUIRES_NEW 事务内动账 + 落 WITHDRAW 流水），捕获唯一键冲突视为
     * 幂等成功，事务提交后过账结算凭证，过账失败仅 WARN 不回滚。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    @Override
    public String withdraw(WithdrawRequestDTO request) {
        validateCommon(request);
        Assert.isTrue(request.getAmount().signum() > 0,
                () -> BizException.get(BizErrorEnum.WITHDRAW_AMOUNT_INVALID));

        // 幂等前置检查：相同 bizNo 已处理过则直接返回
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            self.withdrawAtomic(request);
        } catch (BizIdempotentException e) {
            // 唯一键冲突说明流水已存在，视为幂等成功
        }

        postJournalSafely(buildWithdrawJournalRequest(request), request.getBizNo());
        return request.getBizNo();
    }

    /**
     * 解冻用例：将冻结余额退回可用余额。
     * <p>
     * 入口方法：参数校验 → bizNo 幂等前置检查 → 自代理调用解冻原子方法
     * （REQUIRES_NEW 事务内动账 + 落 UNFREEZE 流水），捕获唯一键冲突视为
     * 幂等成功，事务提交后过账解冻凭证，过账失败仅 WARN 不回滚。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency
     * @return 业务流水号
     */
    @Override
    public String unfreeze(WithdrawRequestDTO request) {
        validateCommon(request);
        Assert.isTrue(request.getAmount().signum() > 0,
                () -> BizException.get(BizErrorEnum.UNFREEZE_AMOUNT_INVALID));

        // 幂等前置检查：相同 bizNo 已处理过则直接返回
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            self.unfreezeAtomic(request);
        } catch (BizIdempotentException e) {
            // 唯一键冲突说明流水已存在，视为幂等成功
        }

        postJournalSafely(buildUnfreezeJournalRequest(request), request.getBizNo());
        return request.getBizNo();
    }

    /**
     * 广播用例：构造交易、签名并提交链上，为提现请求留痕。
     * <p>
     * 编排流程（design D8 事务边界）：
     * 参数校验与幂等前置检查 → 事务 T1 保存 PENDING 提现请求 →
     * 事务外查询 nonce、签名、广播（外部 IO 不碰 DB）→
     * 广播成功：事务 T2 置 BROADCASTED 并回填 txHash，随后创建 DRAFT 凭证
     * （携带 chainId/chainTxHash/tokenAddress，失败仅 WARN 交由对账）；
     * 广播失败：事务 T3 置 FAILED，签名异常抛 TX_SIGN_FAILED，
     * 广播异常抛 TX_BROADCAST_FAILED（不创建凭证）。
     *
     * @param request 提现请求，包含 uid、bizNo、amount、currency、chainId、toAddress
     * @return 广播结果，包含 bizNo、txHash 与请求状态
     */
    @Override
    public WithdrawBroadcastResponseDTO broadcast(WithdrawRequestDTO request) {
        validateCommon(request);
        Assert.isTrue(request.getAmount().signum() > 0,
                () -> BizException.get(BizErrorEnum.WITHDRAW_AMOUNT_INVALID));
        Assert.notBlank(request.getChainId(), () -> BizException.get(BizErrorEnum.PARAM_MISS));
        Assert.notBlank(request.getToAddress(), () -> BizException.get(BizErrorEnum.PARAM_MISS));

        // 幂等前置检查：相同 bizNo 已存在时按状态分流——
        // 终态（SETTLED/CANCELLED）与已广播（BROADCASTED）请求幂等返回；
        // PENDING（广播中断）/FAILED（广播失败）放行继续广播，避免重试死路径
        if (withdrawRequestRepo.existsByBizNo(request.getBizNo())) {
            WithdrawRequestEntity existing = findWithdrawRequestOrThrow(request.getBizNo());
            if (existing.getStatus() != WithdrawRequestStatusEnum.PENDING
                    && existing.getStatus() != WithdrawRequestStatusEnum.FAILED) {
                return buildBroadcastResponse(existing);
            }
        }

        // 冻结前置校验：提现广播必须建立在已冻结资金之上，
        // 防止未冻结的提现请求直接驱动热钱包转账造成资金盗取（资金安全红线）
        AccountEntity account = getAccountOrThrow(request.getUid());
        if (account.getFrozenBalance().compareTo(request.getAmount()) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE);
        }

        // 事务 T1：保存 PENDING 提现请求，广播前先落库，崩溃后可恢复
        try {
            self.savePendingRequestAtomic(request);
        } catch (BizIdempotentException e) {
            // 唯一键冲突说明 PENDING 已存在（并发或重试），继续执行广播流程；
            // 链上 nonce 幂等保证重复广播不产生双花，markBroadcastedAtomic 状态机单次生效
        }

        // 事务外：查询 nonce（外部 IO，不碰 DB）
        BigInteger nonce;
        try {
            nonce = txBroadcastPort.currentNonce(request.getChainId(), fromAddress);
        } catch (Exception e) {
            // 广播链路异常：置 FAILED 后抛出广播失败
            self.markFailedAtomic(request.getBizNo());
            throw BizException.get(BizErrorEnum.TX_BROADCAST_FAILED);
        }

        // 事务外：签名（外部 IO，不碰 DB；私钥由签名设施持有，OTX 不接触）
        SignedTx signedTx;
        try {
            signedTx = signerPort.sign(buildSignRequest(request, nonce));
        } catch (Exception e) {
            // 签名异常：置 FAILED 后抛出签名失败
            self.markFailedAtomic(request.getBizNo());
            throw BizException.get(BizErrorEnum.TX_SIGN_FAILED);
        }

        // 事务外：广播（外部 IO，不碰 DB）
        BroadcastResult broadcastResult;
        try {
            broadcastResult = txBroadcastPort.broadcast(signedTx);
        } catch (Exception e) {
            // 广播异常：置 FAILED 后抛出广播失败
            self.markFailedAtomic(request.getBizNo());
            throw BizException.get(BizErrorEnum.TX_BROADCAST_FAILED);
        }

        // 事务 T2：置 BROADCASTED 并回填链上交易哈希
        self.markBroadcastedAtomic(request.getBizNo(), broadcastResult.getTxHash());

        // 广播成功后创建 DRAFT 凭证留痕（独立事务），失败仅 WARN 交由对账修复
        createDraftJournalSafely(request, broadcastResult.getTxHash());

        return buildBroadcastResponse(findWithdrawRequestOrThrow(request.getBizNo()));
    }

    /**
     * 确认结算用例：链上确认达标后完成冻结资金扣减与凭证过账。
     * <p>
     * 编排流程：加载提现请求（SETTLED 幂等返回）→ 事务外查询链上回执
     * 判断交易是否存在与成功 → 确认数未达标抛 TX_NOT_CONFIRMED_YET →
     * 确认达标：事务 T4 复用 withdraw-two-phase 结算范式扣减冻结余额、
     * 落 WITHDRAW 流水并置 SETTLED，随后过账 DRAFT 凭证（失败仅 WARN）；
     * 链上交易失败或缺失：解冻资金、置 FAILED 并抛 TX_CHAIN_FAILED
     * （DRAFT 凭证保留作为审计痕迹，不删除）。
     *
     * @param bizNo 业务流水号
     * @return 结算结果，包含 bizNo、请求状态与凭证状态
     */
    @Override
    public WithdrawSettleResponseDTO confirmAndSettle(String bizNo) {
        WithdrawRequestEntity request = findWithdrawRequestOrThrow(bizNo);

        // 幂等返回：已结算的请求直接返回已有结果，不重复扣款
        if (request.getStatus() == WithdrawRequestStatusEnum.SETTLED) {
            return buildSettleResponse(request, resolveJournalStatus(bizNo));
        }

        // 仅 BROADCASTED 可结算：PENDING（未广播）/FAILED（广播失败或链上失败）/
        // CANCELLED（已取消）均不可进入链上查询，避免 null txHash 直传 RPC 端口
        // 导致边界行为不可预期（txHash 仅在广播成功后回填）
        if (request.getStatus() != WithdrawRequestStatusEnum.BROADCASTED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }

        // 事务外：查询链上回执，判断交易是否存在且成功（外部 IO，不碰 DB）
        Optional<ChainTxReceipt> receiptOpt = chainQueryPort.queryTxReceipt(
                request.getChainId(), request.getTxHash());
        if (receiptOpt.isEmpty() || !isSuccessReceipt(receiptOpt.get())) {
            // 交易缺失或回执失败：解冻资金、置 FAILED，DRAFT 凭证保留
            self.markFailedAndUnfreezeAtomic(bizNo);
            throw BizException.get(BizErrorEnum.TX_CHAIN_FAILED);
        }

        // 事务外：确认数校验（外部 IO，不碰 DB）
        if (!chainQueryPort.isConfirmed(request.getChainId(), request.getTxHash(), requiredConfirmations)) {
            // 确认数未达标：可重试，状态不变
            throw BizException.get(BizErrorEnum.TX_NOT_CONFIRMED_YET);
        }

        // 事务 T4：扣减冻结余额 + WITHDRAW 流水 + 请求置 SETTLED
        self.settleAtomic(bizNo);

        // 过账 DRAFT 凭证（独立事务），失败仅 WARN 交由对账修复
        postJournalSafelyByBizNo(bizNo);

        return buildSettleResponse(findWithdrawRequestOrThrow(bizNo), resolveJournalStatus(bizNo));
    }

    /**
     * 取消用例：解冻已冻结资金并置请求为 CANCELLED。
     * <p>
     * 已取消请求幂等返回成功；已结算请求为终态不可取消，抛
     * WITHDRAW_REQUEST_STATUS_INVALID。取消原子方法复用 phase1 解冻范式：
     * 解冻资金 + UNFREEZE 流水 + 请求置 CANCELLED。
     *
     * @param bizNo 业务流水号
     * @return 取消后的提现请求状态
     */
    @Override
    public WithdrawStatusResponseDTO cancelWithdraw(String bizNo) {
        WithdrawRequestEntity request = findWithdrawRequestOrThrow(bizNo);

        // 幂等返回：已取消的请求直接返回成功，不重复解冻
        if (request.getStatus() == WithdrawRequestStatusEnum.CANCELLED) {
            return buildStatusResponse(request);
        }
        // 已结算的请求不可取消
        if (request.getStatus() == WithdrawRequestStatusEnum.SETTLED) {
            throw BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID);
        }

        // 事务 T6：解冻资金 + UNFREEZE 流水 + 请求置 CANCELLED
        self.cancelAtomic(bizNo);

        return buildStatusResponse(findWithdrawRequestOrThrow(bizNo));
    }

    /**
     * 状态查询用例：按业务流水号查询提现请求的当前状态。
     *
     * @param bizNo 业务流水号
     * @return 提现请求状态，包含 bizNo、status、txHash、amount、currency
     */
    @Override
    public WithdrawStatusResponseDTO queryStatus(String bizNo) {
        return buildStatusResponse(findWithdrawRequestOrThrow(bizNo));
    }

    /**
     * 冻结原子方法，在独立事务中完成余额变更与流水记录。
     * <p>
     * 将申请金额从可用余额转入冻结余额，落 FREEZE 流水（方向 OUT，
     * 快照为可用余额变动前后）。乐观锁冲突时由 Spring Retry 自动重试，最多 5 次。
     *
     * @param request 提现请求
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void freezeAtomic(WithdrawRequestDTO request) {
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        account.freezeBalance(request.getAmount());
        BigDecimal after = account.getAvailableBalance();

        recordFlow(request, before, after, FundFlowDirectionEnum.OUT, FundFlowTypeEnum.FREEZE);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
    }

    /**
     * 结算原子方法，在独立事务中完成余额变更与流水记录。
     * <p>
     * 从冻结余额扣减指定金额，落 WITHDRAW 流水（方向 OUT，
     * 快照为冻结余额变动前后）。乐观锁冲突时由 Spring Retry 自动重试，最多 5 次。
     *
     * @param request 提现请求
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void withdrawAtomic(WithdrawRequestDTO request) {
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getFrozenBalance();
        account.withdraw(request.getAmount());
        BigDecimal after = account.getFrozenBalance();

        recordFlow(request, before, after, FundFlowDirectionEnum.OUT, FundFlowTypeEnum.WITHDRAW);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
    }

    /**
     * 解冻原子方法，在独立事务中完成余额变更与流水记录。
     * <p>
     * 将冻结余额退回可用余额，落 UNFREEZE 流水（方向 IN，
     * 快照为可用余额变动前后）。乐观锁冲突时由 Spring Retry 自动重试，最多 5 次。
     *
     * @param request 提现请求
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void unfreezeAtomic(WithdrawRequestDTO request) {
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        account.unfreezeBalance(request.getAmount());
        BigDecimal after = account.getAvailableBalance();

        recordFlow(request, before, after, FundFlowDirectionEnum.IN, FundFlowTypeEnum.UNFREEZE);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
    }

    /**
     * 事务 T1 原子方法：保存 PENDING 提现请求。
     * <p>
     * 在独立事务中通过聚合根工厂创建提现请求并落库，
     * 广播前先落库保证崩溃后可恢复；唯一键冲突转换为幂等异常。
     *
     * @param request 提现请求
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void savePendingRequestAtomic(WithdrawRequestDTO request) {
        try {
            // 通过聚合根工厂创建 PENDING 状态的提现请求
            WithdrawRequestEntity entity = WithdrawRequestEntity.create(
                    request.getUid(), request.getBizNo(), request.getAmount(),
                    request.getCurrency(), request.getChainId(),
                    request.getToAddress(), request.getTokenAddress());
            withdrawRequestRepo.save(entity);
        } catch (DuplicateKeyException ex) {
            // 唯一键冲突说明请求已存在，视为幂等成功
            throw new BizIdempotentException(ex);
        }
    }

    /**
     * 事务 T2 原子方法：广播成功后回填交易哈希并置 BROADCASTED。
     *
     * @param bizNo  业务流水号
     * @param txHash 链上交易哈希
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markBroadcastedAtomic(String bizNo, String txHash) {
        WithdrawRequestEntity entity = findWithdrawRequestOrThrow(bizNo);
        // 状态机校验：PENDING/FAILED 允许进入 BROADCASTED，非法转移抛 WITHDRAW_REQUEST_STATUS_INVALID
        entity.markBroadcasted(txHash);
        withdrawRequestRepo.update(entity);
    }

    /**
     * 事务 T3 原子方法：广播失败后置 FAILED（资金保持冻结，由取消路径解冻）。
     *
     * @param bizNo 业务流水号
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailedAtomic(String bizNo) {
        WithdrawRequestEntity entity = findWithdrawRequestOrThrow(bizNo);
        // 状态机校验：PENDING/BROADCASTED 允许进入 FAILED
        entity.markFailed();
        withdrawRequestRepo.update(entity);
    }

    /**
     * 事务 T4 原子方法：链上确认达标后完成结算。
     * <p>
     * 复用 withdraw-two-phase 变更的结算范式（withdrawAtomic 同构）：
     * 从冻结余额扣减提现金额 + 落 WITHDRAW 流水（方向 OUT，快照冻结余额
     * 变动前后）+ 请求置 SETTLED。与 withdrawAtomic 的区别：数据源为
     * 提现请求实体而非请求 DTO，避免复用 DTO 签名的别扭。
     *
     * @param bizNo 业务流水号
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void settleAtomic(String bizNo) {
        WithdrawRequestEntity request = findWithdrawRequestOrThrow(bizNo);
        // 并发结算：另一线程已置 SETTLED 时幂等返回，不再抛状态非法；
        // 配合乐观锁重试，保证并发 confirmAndSettle 败方重试后幂等成功而非抛异常
        if (request.getStatus() == WithdrawRequestStatusEnum.SETTLED) {
            return;
        }
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getFrozenBalance();
        account.withdraw(request.getAmount());
        BigDecimal after = account.getFrozenBalance();

        recordEntityFlow(request, before, after, FundFlowDirectionEnum.OUT, FundFlowTypeEnum.WITHDRAW);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
        // 状态机校验：仅 BROADCASTED 允许进入 SETTLED
        request.markSettled();
        withdrawRequestRepo.update(request);
    }

    /**
     * 链上失败原子方法：交易失败或缺失时解冻资金并置 FAILED。
     * <p>
     * 复用 phase1 解冻范式（unfreezeAtomic 同构）：解冻冻结余额退回
     * 可用余额 + 落 UNFREEZE 流水（方向 IN，快照可用余额变动前后）+
     * 请求置 FAILED。DRAFT 凭证保留作为审计痕迹，不删除。
     *
     * @param bizNo 业务流水号
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailedAndUnfreezeAtomic(String bizNo) {
        WithdrawRequestEntity request = findWithdrawRequestOrThrow(bizNo);
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        account.unfreezeBalance(request.getAmount());
        BigDecimal after = account.getAvailableBalance();

        recordEntityFlow(request, before, after, FundFlowDirectionEnum.IN, FundFlowTypeEnum.UNFREEZE);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
        // 状态机校验：仅 PENDING/BROADCASTED 允许进入 FAILED
        request.markFailed();
        withdrawRequestRepo.update(request);
    }

    /**
     * 事务 T6 原子方法：取消提现时解冻资金并置 CANCELLED。
     * <p>
     * 复用 phase1 解冻范式：解冻冻结余额退回可用余额 + 落 UNFREEZE 流水
     * （方向 IN，快照可用余额变动前后）+ 请求置 CANCELLED。
     *
     * @param bizNo 业务流水号
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void cancelAtomic(String bizNo) {
        WithdrawRequestEntity request = findWithdrawRequestOrThrow(bizNo);
        AccountEntity account = getAccountOrThrow(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        account.unfreezeBalance(request.getAmount());
        BigDecimal after = account.getAvailableBalance();

        recordEntityFlow(request, before, after, FundFlowDirectionEnum.IN, FundFlowTypeEnum.UNFREEZE);

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
        // 状态机校验：PENDING/BROADCASTED/FAILED 允许取消，SETTLED 不可取消
        request.cancel();
        withdrawRequestRepo.update(request);
    }

    /**
     * 按业务流水号查询提现请求，不存在则抛 WITHDRAW_REQUEST_NOT_FOUND。
     *
     * @param bizNo 业务流水号
     * @return 提现请求聚合根
     */
    private WithdrawRequestEntity findWithdrawRequestOrThrow(String bizNo) {
        return withdrawRequestRepo.findByBizNo(bizNo)
                .orElseThrow(() -> BizException.get(BizErrorEnum.WITHDRAW_REQUEST_NOT_FOUND));
    }

    /**
     * 解析结算响应的凭证状态：从总账实时查询，避免响应失真。
     * <p>
     * 凭证过账是最终一致路径（失败仅 WARN 不回滚），硬编码 POSTED 会掩盖
     * 凭证缺失/未过账的真相；查询不到（过账失败容错路径）时返回 UNKNOWN
     * 供对账识别，不向上抛异常。
     *
     * @param bizNo 业务流水号
     * @return 凭证状态，查询异常时为 UNKNOWN
     */
    private String resolveJournalStatus(String bizNo) {
        try {
            return ledgerAppService.findByBizNo(bizNo).getStatus();
        } catch (Exception e) {
            // 凭证缺失（过账失败仅 WARN 的容错路径），返回 UNKNOWN 供对账识别
            return "UNKNOWN";
        }
    }

    /**
     * 记录提现请求关联的资金流水，biz_no 唯一索引冲突时转换为幂等异常。
     * <p>
     * 与 {@link #recordFlow} 同构，但数据源为提现请求实体
     * （结算/链上失败/取消原子方法使用）。
     *
     * @param request   提现请求实体
     * @param before    变动前余额快照
     * @param after     变动后余额快照
     * @param direction 资金变动方向
     * @param type      资金流水类型
     */
    private void recordEntityFlow(WithdrawRequestEntity request, BigDecimal before, BigDecimal after,
                                  FundFlowDirectionEnum direction, FundFlowTypeEnum type) {
        try {
            // 先记录资金流水（biz_no 唯一索引实现幂等）
            fundFlowAppService.record(CreateFundFlowRequest.builder()
                    .uid(request.getUid())
                    .bizNo(request.getBizNo())
                    .amount(request.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .direction(direction)
                    .type(type)
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }
    }

    /**
     * 构建签名请求：组装链 ID、发送方/接收方地址与转账金额（Wei）。
     * <p>
     * 金额默认按 18 位精度转换（amount × 10^18 取整），多精度币种
     * （如 6 位小数的 USDT）留待后续变更统一处理。
     *
     * @param request 提现请求
     * @param nonce   交易 nonce（由广播端口查询）
     * @return 签名请求
     */
    private SignRequest buildSignRequest(WithdrawRequestDTO request, BigInteger nonce) {
        return new SignRequest(
                request.getChainId(), fromAddress, request.getToAddress(),
                toAmountWei(request.getAmount()), request.getTokenAddress(),
                nonce, null, null);
    }

    /**
     * 将币数量转换为 Wei 精度：默认按 18 位小数处理，取整不四舍五入。
     *
     * @param amount 币数量（DECIMAL(38,18)）
     * @return Wei 精度的转账金额
     */
    private BigInteger toAmountWei(BigDecimal amount) {
        return amount.movePointRight(18).toBigIntegerExact();
    }

    /**
     * 广播成功后创建 DRAFT 凭证留痕：失败仅记录 WARN 日志，
     * 缺失凭证由对账模块后续修复（最终一致性，与既有过账容错一致）。
     *
     * @param request 提现请求
     * @param txHash  链上交易哈希
     */
    private void createDraftJournalSafely(WithdrawRequestDTO request, String txHash) {
        try {
            ledgerAppService.createDraftJournal(buildBroadcastDraftJournalRequest(request, txHash));
        } catch (Exception e) {
            // DRAFT 凭证创建失败，请求状态与广播记录已提交，日志记录供对账修复
            log.warn("Draft journal creation failed, bizNo={}", request.getBizNo(), e);
        }
    }

    /**
     * 过账草稿凭证失败容错：过账异常仅记录 WARN 日志，结算与流水已提交
     * 不回滚，缺失凭证由对账模块后续修复（最终一致性）。
     *
     * @param bizNo 业务流水号
     */
    private void postJournalSafelyByBizNo(String bizNo) {
        try {
            ledgerAppService.postJournalByBizNo(bizNo);
        } catch (Exception e) {
            // 凭证过账失败，余额和流水已提交，日志记录供后续对账修复
            log.warn("Journal posting by bizNo failed, bizNo={}", bizNo, e);
        }
    }

    /**
     * 构建广播 DRAFT 凭证请求：借记用户冻结余额，贷记提现在途，
     * 携带链上字段（chainId / chainTxHash / tokenAddress）留痕。
     *
     * @param request 提现请求
     * @param txHash  链上交易哈希
     * @return 过账请求
     */
    private PostJournalRequestDTO buildBroadcastDraftJournalRequest(WithdrawRequestDTO request, String txHash) {
        PostJournalRequestDTO dto = new PostJournalRequestDTO();
        dto.setBizNo(request.getBizNo());
        dto.setBizType(LedgerBizTypeEnum.WITHDRAW_ONCHAIN);
        dto.setCurrency(request.getCurrency());
        dto.setPostingDate(LocalDate.now());
        dto.setDescription("提现广播-" + request.getBizNo());
        dto.setChainId(request.getChainId());
        dto.setChainTxHash(txHash);
        dto.setBlockNumber(null);
        dto.setTokenAddress(request.getTokenAddress());

        LedgerEntryRequestDTO debitEntry = new LedgerEntryRequestDTO();
        debitEntry.setAccountCode(LedgerAccountCodeEnum.USER_FROZEN);
        debitEntry.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debitEntry.setAmount(request.getAmount());
        debitEntry.setUid(request.getUid());
        debitEntry.setCounterparty(null);
        debitEntry.setBalanceAfter(null);
        debitEntry.setRemark(null);

        LedgerEntryRequestDTO creditEntry = new LedgerEntryRequestDTO();
        creditEntry.setAccountCode(LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT);
        creditEntry.setEntryType(LedgerEntryTypeEnum.CREDIT);
        creditEntry.setAmount(request.getAmount());
        creditEntry.setUid(null);
        creditEntry.setCounterparty(null);
        creditEntry.setBalanceAfter(null);
        creditEntry.setRemark(null);

        dto.setEntries(List.of(debitEntry, creditEntry));
        return dto;
    }

    /**
     * 判断链上回执是否为成功回执：status 为 "0x1" 视为成功。
     *
     * @param receipt 链上交易回执
     * @return true 表示交易成功
     */
    private boolean isSuccessReceipt(ChainTxReceipt receipt) {
        return "0x1".equals(receipt.getStatus());
    }

    /**
     * 装配广播响应 DTO。
     *
     * @param entity 提现请求实体
     * @return 广播响应
     */
    private WithdrawBroadcastResponseDTO buildBroadcastResponse(WithdrawRequestEntity entity) {
        WithdrawBroadcastResponseDTO dto = new WithdrawBroadcastResponseDTO();
        dto.setBizNo(entity.getBizNo());
        dto.setTxHash(entity.getTxHash());
        dto.setStatus(entity.getStatus().name());
        return dto;
    }

    /**
     * 装配结算响应 DTO。
     *
     * @param entity        提现请求实体
     * @param journalStatus 凭证状态
     * @return 结算响应
     */
    private WithdrawSettleResponseDTO buildSettleResponse(WithdrawRequestEntity entity, String journalStatus) {
        WithdrawSettleResponseDTO dto = new WithdrawSettleResponseDTO();
        dto.setBizNo(entity.getBizNo());
        dto.setStatus(entity.getStatus().name());
        dto.setJournalStatus(journalStatus);
        return dto;
    }

    /**
     * 装配状态响应 DTO。
     *
     * @param entity 提现请求实体
     * @return 状态响应
     */
    private WithdrawStatusResponseDTO buildStatusResponse(WithdrawRequestEntity entity) {
        WithdrawStatusResponseDTO dto = new WithdrawStatusResponseDTO();
        dto.setBizNo(entity.getBizNo());
        dto.setStatus(entity.getStatus().name());
        dto.setTxHash(entity.getTxHash());
        dto.setAmount(entity.getAmount());
        dto.setCurrency(entity.getCurrency());
        return dto;
    }

    /**
     * 公共入参校验：request 及其 uid、bizNo、amount、currency 均不得为空，
     * 且 uid 必须为正数。
     *
     * @param request 提现请求
     */
    private void validateCommon(WithdrawRequestDTO request) {
        Assert.notNull(request, () -> BizException.get(BizErrorEnum.PARAM_MISS));
        Assert.notNull(request.getUid(), () -> BizException.get(BizErrorEnum.UID_CANT_NULL));
        Assert.isTrue(request.getUid() > 0, () -> BizException.get(BizErrorEnum.UID_INVALID));
        Assert.notBlank(request.getBizNo(), () -> BizException.get(BizErrorEnum.BIZ_NO_EMPTY));
        Assert.notNull(request.getAmount(), () -> BizException.get(BizErrorEnum.AMOUNT_CANT_NULL));
        Assert.notBlank(request.getCurrency(), () -> BizException.get(BizErrorEnum.PARAM_MISS));
    }

    /**
     * 按用户标识查询账户，不存在则抛出账户不存在异常。
     *
     * @param uid 用户唯一标识
     * @return 账户实体
     * @throws BizException 当账户不存在时抛出 ACCOUNT_NOT_EXIST
     */
    private AccountEntity getAccountOrThrow(Long uid) {
        AccountEntity account = accountRepo.findByUid(uid);
        if (account == null) {
            throw BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        }
        return account;
    }

    /**
     * 记录资金流水，biz_no 唯一索引冲突时转换为幂等异常。
     *
     * @param request   提现请求
     * @param before    变动前余额快照
     * @param after     变动后余额快照
     * @param direction 资金变动方向
     * @param type      资金流水类型
     */
    private void recordFlow(WithdrawRequestDTO request, BigDecimal before, BigDecimal after,
                            FundFlowDirectionEnum direction, FundFlowTypeEnum type) {
        try {
            // 先记录资金流水（biz_no 唯一索引实现幂等）
            fundFlowAppService.record(CreateFundFlowRequest.builder()
                    .uid(request.getUid())
                    .bizNo(request.getBizNo())
                    .amount(request.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .direction(direction)
                    .type(type)
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }
    }

    /**
     * 过账失败容错：总账过账异常仅记录 WARN 日志，余额与流水已提交不回滚，
     * 缺失凭证由对账模块后续修复（最终一致性）。
     *
     * @param journalRequest 过账请求
     * @param bizNo          业务流水号
     */
    private void postJournalSafely(PostJournalRequestDTO journalRequest, String bizNo) {
        try {
            ledgerAppService.postJournal(journalRequest);
        } catch (Exception e) {
            // 总账过账失败，余额和流水已提交，日志记录供后续对账修复
            log.warn("Journal posting failed, bizNo={}", bizNo, e);
        }
    }

    /**
     * 构建冻结凭证请求：借记用户可用余额，贷记用户冻结余额。
     *
     * @param request 提现请求
     * @return 过账请求
     */
    private PostJournalRequestDTO buildFreezeJournalRequest(WithdrawRequestDTO request) {
        return buildJournalRequest(request, LedgerBizTypeEnum.FREEZE, "提现冻结-" + request.getBizNo(),
                LedgerAccountCodeEnum.USER_AVAILABLE, request.getUid(),
                LedgerAccountCodeEnum.USER_FROZEN, request.getUid());
    }

    /**
     * 构建结算凭证请求：借记用户冻结余额，贷记提现在途。
     *
     * @param request 提现请求
     * @return 过账请求
     */
    private PostJournalRequestDTO buildWithdrawJournalRequest(WithdrawRequestDTO request) {
        return buildJournalRequest(request, LedgerBizTypeEnum.WITHDRAW_ONCHAIN, "提现结算-" + request.getBizNo(),
                LedgerAccountCodeEnum.USER_FROZEN, request.getUid(),
                LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT, null);
    }

    /**
     * 构建解冻凭证请求：借记用户冻结余额，贷记用户可用余额。
     *
     * @param request 提现请求
     * @return 过账请求
     */
    private PostJournalRequestDTO buildUnfreezeJournalRequest(WithdrawRequestDTO request) {
        return buildJournalRequest(request, LedgerBizTypeEnum.UNFREEZE, "提现解冻-" + request.getBizNo(),
                LedgerAccountCodeEnum.USER_FROZEN, request.getUid(),
                LedgerAccountCodeEnum.USER_AVAILABLE, request.getUid());
    }

    /**
     * 构建通用过账请求：设置业务类型、币种、记账日期与借贷两条分录，
     * 链上字段（chainId / chainTxHash / blockNumber / tokenAddress）置空。
     *
     * @param request       提现请求
     * @param bizType       凭证业务类型
     * @param description   凭证描述
     * @param debitAccount  借方科目
     * @param debitUid      借方用户标识
     * @param creditAccount 贷方科目
     * @param creditUid     贷方用户标识
     * @return 过账请求
     */
    private PostJournalRequestDTO buildJournalRequest(WithdrawRequestDTO request, LedgerBizTypeEnum bizType,
                                                      String description, LedgerAccountCodeEnum debitAccount, Long debitUid,
                                                      LedgerAccountCodeEnum creditAccount, Long creditUid) {
        PostJournalRequestDTO dto = new PostJournalRequestDTO();
        dto.setBizNo(request.getBizNo());
        dto.setBizType(bizType);
        dto.setCurrency(request.getCurrency());
        dto.setPostingDate(LocalDate.now());
        dto.setDescription(description);
        dto.setChainId(null);
        dto.setChainTxHash(null);
        dto.setBlockNumber(null);
        dto.setTokenAddress(null);

        LedgerEntryRequestDTO debitEntry = new LedgerEntryRequestDTO();
        debitEntry.setAccountCode(debitAccount);
        debitEntry.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debitEntry.setAmount(request.getAmount());
        debitEntry.setUid(debitUid);
        debitEntry.setCounterparty(null);
        debitEntry.setBalanceAfter(null);
        debitEntry.setRemark(null);

        LedgerEntryRequestDTO creditEntry = new LedgerEntryRequestDTO();
        creditEntry.setAccountCode(creditAccount);
        creditEntry.setEntryType(LedgerEntryTypeEnum.CREDIT);
        creditEntry.setAmount(request.getAmount());
        creditEntry.setUid(creditUid);
        creditEntry.setCounterparty(null);
        creditEntry.setBalanceAfter(null);
        creditEntry.setRemark(null);

        dto.setEntries(List.of(debitEntry, creditEntry));
        return dto;
    }
}
