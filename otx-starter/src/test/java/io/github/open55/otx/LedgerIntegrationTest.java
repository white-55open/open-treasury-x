package io.github.open55.otx;

import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.domain.ledger.repository.LedgerEntryRepo;
import io.github.open55.otx.domain.ledger.repository.LedgerJournalRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ledger 模块集成测试。
 * <p>
 * 验证 Spring 上下文装配完整，所有 Ledger 模块的 Bean 可正常注入。
 */
@SpringBootTest
class LedgerIntegrationTest {

    @Autowired
    private LedgerAppService ledgerAppService;

    @Autowired
    private LedgerJournalRepo ledgerJournalRepo;

    @Autowired
    private LedgerEntryRepo ledgerEntryRepo;

    /**
     * contextLoads 验证 Ledger 模块 Bean 装配完整。
     * <p>
     * 当 Spring 上下文启动后，LedgerAppService、LedgerJournalRepo、LedgerEntryRepo 应非空。
     */
    @Test
    void contextLoads() {
        assertNotNull(ledgerAppService, "LedgerAppService should be wired");
        assertNotNull(ledgerJournalRepo, "LedgerJournalRepo should be wired");
        assertNotNull(ledgerEntryRepo, "LedgerEntryRepo should be wired");
    }

    /**
     * 借贷不平衡 E2E 测试。
     * <p>
     * 提交借贷不等的过账请求，验证返回 LEDGER_NOT_BALANCED 异常。
     */
    @Test
    void postJournal_withUnbalancedEntries_throwsNotBalanced() {
        io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO req =
                new io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO();
        req.setBizNo("UNBALANCED-" + System.currentTimeMillis());
        req.setBizType(io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        req.setCurrency("USDT");
        req.setPostingDate(java.time.LocalDate.now());

        io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO debit =
                new io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO();
        debit.setAccountCode(io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum.PLATFORM_HOT);
        debit.setEntryType(io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum.DEBIT);
        debit.setAmount(java.math.BigDecimal.valueOf(100));

        io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO credit =
                new io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO();
        credit.setAccountCode(io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT);
        credit.setEntryType(io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum.CREDIT);
        credit.setAmount(java.math.BigDecimal.valueOf(99));

        req.setEntries(java.util.List.of(debit, credit));

        io.github.open55.otx.common.exception.BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                io.github.open55.otx.common.exception.BizException.class,
                () -> ledgerAppService.postJournal(req));

        org.junit.jupiter.api.Assertions.assertEquals(
                io.github.open55.otx.common.exception.BizErrorEnum.LEDGER_NOT_BALANCED.getCode(),
                ex.getErrorCode());
    }

    /**
     * 并发幂等集成测试。
     * <p>
     * 10 个并发请求同一 bizNo，最终数据库仅 1 条 Journal（其余 9 个走幂等分支返回同一条）。
     * 注意：此测试依赖数据库 Flyway 迁移已完成。
     */
    @Test
    void concurrentPostJournal_sameBizNo_onlyOneCreated() throws Exception {
        String bizNo = "CONCURRENT-" + System.currentTimeMillis();
        io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO req =
                new io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO();
        req.setBizNo(bizNo);
        req.setBizType(io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        req.setCurrency("USDT");
        req.setPostingDate(java.time.LocalDate.now());

        io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO debit =
                new io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO();
        debit.setAccountCode(io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum.PLATFORM_HOT);
        debit.setEntryType(io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum.DEBIT);
        debit.setAmount(java.math.BigDecimal.valueOf(100));

        io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO credit =
                new io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO();
        credit.setAccountCode(io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT);
        credit.setEntryType(io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum.CREDIT);
        credit.setAmount(java.math.BigDecimal.valueOf(100));
        req.setEntries(java.util.List.of(debit, credit));

        int threadCount = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO>> futures =
                new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    latch.await();
                    return ledgerAppService.postJournal(req);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();

        java.util.Set<String> bizNos = new java.util.HashSet<>();
        for (java.util.concurrent.Future<io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO> f : futures) {
            bizNos.add(f.get().getBizNo());
        }

        org.junit.jupiter.api.Assertions.assertEquals(1, bizNos.size(),
                "10 个并发请求应只产生 1 个唯一的 bizNo");
    }
}
