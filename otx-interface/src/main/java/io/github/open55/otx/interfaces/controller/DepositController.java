package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.deposit.service.DepositApplicationService;
import io.github.open55.otx.application.deposit.service.dto.CreateDepositRequest;
import io.github.open55.otx.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositApplicationService depositApplicationService;

    @PostMapping
    public Result<String> deposit(@RequestBody CreateDepositRequest request) {
        return Result.success(depositApplicationService.deposit(request));
    }
}