package io.github.open55.otx.application.account.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.assembler.AccountAssembler;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 账户应用服务实现，编排账户相关的业务用例。
 * <p>
 * 负责账户创建、余额变更、冻结/解冻及资金流水记录的编排。
 * 核心方法 changeAmountWithFundFlowAtomic 通过 REQUIRES_NEW 事务传播
 * 和 Spring Retry 机制处理乐观锁冲突。
 */
@Service
@RequiredArgsConstructor
public class AccountAppServiceImpl implements AccountAppService {

    private final AccountRepo accountRepo;

    @Lazy
    @Resource
    private FundFlowAppService fundFlowAppService;

    @Lazy
    @Resource
    private AccountAppServiceImpl self;

    /**
     * 为用户创建账户，若账户已存在则直接返回现有标识。
     *
     * @param uid 用户唯一标识
     * @return 账户标识
     */
    @Override
    public Long createAccount(Long uid) {
        AccountEntity exist = accountRepo.findByUid(uid);
        if (exist != null) {
            return exist.getId();
        }

        AccountEntity accountEntity = AccountEntity.create(uid);
        accountRepo.save(accountEntity);
        return accountEntity.getId();
    }

    /**
     * 增加用户可用余额。
     *
     * @param uid    用户唯一标识
     * @param amount 增加金额
     */
    @Override
    public void increaseBalance(Long uid, BigDecimal amount) {
        AccountEntity accountEntity = getAccountEntityOrThrow(uid);
        accountEntity.increaseBalance(amount);
        accountRepo.update(accountEntity);
    }

    /**
     * 冻结用户指定金额。
     *
     * @param uid    用户唯一标识
     * @param amount 冻结金额
     */
    @Override
    public void freezeBalance(Long uid, BigDecimal amount) {
        AccountEntity accountEntity = getAccountEntityOrThrow(uid);
        accountEntity.freezeBalance(amount);
        accountRepo.update(accountEntity);
    }

    /**
     * 按用户标识查询账户详情。
     *
     * @param uid 用户唯一标识
     * @return 账户详情
     */
    @Override
    public GetAccountResponse getByUid(Long uid) {
        AccountEntity entity = getAccountEntityOrThrow(uid);
        return AccountAssembler.INSTANCE.entity2AccountResponse(entity);
    }

    /**
     * 按用户标识查询账户，不存在则抛出异常。
     *
     * @param uid 用户唯一标识
     * @return 账户实体
     * @throws BizException 当账户不存在时抛出 ACCOUNT_NOT_EXIST
     */
    private AccountEntity getAccountEntityOrThrow(Long uid) {
        AccountEntity accountEntity = accountRepo.findByUid(uid);

        if (accountEntity == null) {
            throw BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        }

        return accountEntity;
    }

    /**
     * 变更金额并记录资金流水（含幂等处理）。
     * <p>
     * 先通过 bizNo 做幂等检查，已存在则直接返回。
     * 否则委托 self.changeAmountWithFundFlowAtomic 在新事务中执行，
     * 捕获唯一键冲突转换的 BizIdempotentException 并忽略。
     *
     * @param request 资金变更请求，包含 uid、金额、bizNo、资金流水类型
     * @return 业务流水号
     */
    @Override
    public String changeAmountWithFundFlow(ChangeAmountRequest request) {
        Assert.notNull(request, () -> BizException.get(BizErrorEnum.PARAM_MISS));
        Assert.notNull(request.getBizNo(), () -> BizException.get(BizErrorEnum.BIZ_NO_EMPTY));
        Assert.notNull(request.getAmount(), () -> BizException.get(BizErrorEnum.AMOUNT_CANT_NULL));
        Assert.notNull(request.getUid(), () -> BizException.get(BizErrorEnum.UID_CANT_NULL));
        Assert.isTrue(request.getUid() > 0, () -> BizException.get(BizErrorEnum.UID_INVALID));
        Assert.notNull(request.getFundFlowType(), () -> BizException.get(BizErrorEnum.FUND_FLOW_TYPE_CANT_NULL));
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            self.changeAmountWithFundFlowAtomic(request);
        } catch (BizIdempotentException e) {
            // 唯一键冲突说明流水已存在，忽略后直接返回
        }

        return request.getBizNo();
    }

    /**
     * 原子化变更金额并记录资金流水。
     * <p>
     * 开启新事务（REQUIRES_NEW），先查账户余额，根据资金流水类型
     * 执行充值（DEPOSIT）或提现（WITHDRAW）领域方法，记录资金流水
     * （唯一键冲突转幂等异常），最后更新账户（可能触发乐观锁重试）。
     * <p>
     * 乐观锁冲突时由 Spring Retry 自动重试，最多 5 次。
     *
     * @param request 资金变更请求
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void changeAmountWithFundFlowAtomic(ChangeAmountRequest request) {
        AccountEntity account = accountRepo.findByUid(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        FundFlowDirectionEnum direction;
        switch (request.getFundFlowType()) {
            case FundFlowTypeEnum.DEPOSIT:
                direction = FundFlowDirectionEnum.IN;
                account.deposit(request.getAmount());
                break;
            case FundFlowTypeEnum.WITHDRAW:
                direction = FundFlowDirectionEnum.OUT;
                account.withdraw(request.getAmount());
                break;
            default:
                throw BizException.get(BizErrorEnum.FUND_FLOW_TYPE_NOT_SUPPORT);
        }
        BigDecimal after = account.getAvailableBalance();

        try {
            // 先记录资金流水（biz_no 唯一索引实现幂等）
            fundFlowAppService.record(CreateFundFlowRequest.builder()
                    .uid(request.getUid())
                    .bizNo(request.getBizNo())
                    .amount(request.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .direction(direction)
                    .type(request.getFundFlowType())
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }

        // 更新账户，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        accountRepo.update(account);
    }
}
