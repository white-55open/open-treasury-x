package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger 模块 REST 控制器，提供凭证过账与查询接口。
 */
@Tag(name = "ledger", description = "总账模块 — 凭证过账与查询")
@RestController
@RequestMapping("/ledger/journals")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerAppService ledgerAppService;

    /**
     * 过账：创建并过账一张凭证。
     * <p>
     * 幂等保证：相同 bizNo 的重复请求返回已存在的 Journal，不产生副作用。
     *
     * @param req 过账请求体，包含业务流水号、业务类型、币种、分录列表等信息
     * @return 统一响应体，data 为凭证详情
     */
    @Operation(summary = "过账", description = "创建并过账一张凭证，幂等保证相同 bizNo 重复请求返回已存在的 Journal")
    @PostMapping
    public Result<JournalDetailResponseDTO> postJournal(@RequestBody PostJournalRequestDTO req) {
        return Result.success(ledgerAppService.postJournal(req));
    }

    /**
     * 按业务流水号查询凭证详情。
     *
     * @param bizNo 业务流水号
     * @return 统一响应体，data 为凭证详情
     */
    @Operation(summary = "查询凭证", description = "按业务流水号查询凭证详情")
    @GetMapping("/{bizNo}")
    public Result<JournalDetailResponseDTO> findByBizNo(@PathVariable String bizNo) {
        return Result.success(ledgerAppService.findByBizNo(bizNo));
    }
}
