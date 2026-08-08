package io.github.open55.mockupstream.web;

import io.github.open55.mockupstream.scenario.ProcessOrchestrator;
import io.github.open55.mockupstream.scenario.ProcessSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 一键处理控制器：唯一的内部触发入口。
 * <p>
 * 批量推进所有挂起事项（充值入账 / 提现结算 / 取消解冻），处理统计
 * 以 flash 属性携带至个人中心主页展示。
 */
@Controller
@RequiredArgsConstructor
public class ProcessController {

    /**
     * 一键处理编排器
     */
    private final ProcessOrchestrator orchestrator;

    /**
     * 执行一键处理并回主页展示处理统计。
     *
     * @param ra 重定向属性（处理统计）
     * @return 重定向回个人中心主页
     */
    @PostMapping("/internal/process")
    public String process(RedirectAttributes ra) {
        ProcessSummary summary = orchestrator.process();
        ra.addFlashAttribute("summary", summary);
        return "redirect:/";
    }
}
