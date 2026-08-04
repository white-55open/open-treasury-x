package io.github.open55.otx.interfaces.admin;

import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理控制台凭证控制器，提供凭证列表与分录详情。
 */
@Controller
@RequestMapping("/admin/journals")
@RequiredArgsConstructor
public class AdminJournalController {

    private final LedgerAppService ledgerAppService;

    /**
     * 凭证列表页，展示全部凭证主表摘要（不含分录）。
     *
     * @param model 视图模型
     * @return 凭证列表页视图名
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("journals", ledgerAppService.listJournals());
        return "admin/journals";
    }

    /**
     * 凭证详情页，展示单张凭证的主表信息与全部借贷分录，
     * 并计算借贷合计与平衡标志供页面断言展示。
     *
     * @param bizNo 业务流水号
     * @param model 视图模型
     * @return 凭证详情页视图名
     */
    @GetMapping("/{bizNo}")
    public String detail(@PathVariable String bizNo, Model model) {
        JournalDetailResponseDTO journal = ledgerAppService.findByBizNo(bizNo);
        model.addAttribute("journal", journal);
        model.addAttribute("debitTotal", sumByEntryType(journal.getEntries(), "DEBIT"));
        model.addAttribute("creditTotal", sumByEntryType(journal.getEntries(), "CREDIT"));
        model.addAttribute("balanced",
                sumByEntryType(journal.getEntries(), "DEBIT")
                        .compareTo(sumByEntryType(journal.getEntries(), "CREDIT")) == 0);
        return "admin/journal-detail";
    }

    /**
     * 按借贷方向汇总分录金额，用于页面借贷合计断言展示。
     *
     * @param entries   分录列表
     * @param entryType 分录方向（DEBIT / CREDIT）
     * @return 该方向的金额合计
     */
    private BigDecimal sumByEntryType(List<LedgerEntryResponseDTO> entries, String entryType) {
        return entries.stream()
                .filter(e -> entryType.equals(e.getEntryType()))
                .map(LedgerEntryResponseDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
