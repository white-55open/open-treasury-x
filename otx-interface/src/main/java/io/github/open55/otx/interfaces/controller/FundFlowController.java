package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fund-flows")
@RequiredArgsConstructor
public class FundFlowController {
    private final FundFlowAppService fundFlowAppService;
}
