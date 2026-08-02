package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import io.github.open55.otx.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 充值 REST 控制器，提供充值入口。
 */
@RestController
@RequestMapping("/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositAppService depositAppService;

    /**
     * 发起充值请求。
     *
     * @param request 充值请求体，包含 uid、金额、业务号及链上证据（chainId/chainTxHash）等信息
     * @return 业务流水号
     */
    @PostMapping
    public Result<String> deposit(@RequestBody DepositRequestDTO request) {
        return Result.success(depositAppService.deposit(request));
    }
}