package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提现 REST 控制器，提供提现入口。
 */
@RestController
@RequestMapping("/withdraw")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawAppService withdrawAppService;

    /**
     * 发起提现请求。
     *
     * @param request 提现请求体，包含 uid、金额、业务号等信息
     * @return 业务流水号
     */
    @PostMapping
    public Result<String> withdraw(@RequestBody ChangeAmountRequest request) {
        return Result.success(withdrawAppService.withdraw(request));
    }
}