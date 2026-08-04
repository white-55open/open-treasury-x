package io.github.open55.otx.interfaces.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理控制台首页控制器，提供控制台导航入口。
 * <p>
 * 控制台为内部运维工具，仅限内网/本地环境使用，上线前须网关认证（DEVT-007）。
 */
@Controller
public class AdminHomeController {

    /**
     * 控制台导航页，展示账户总览 / 提现单据 / 流水时间线 / 凭证查看四个入口。
     *
     * @return 导航页视图名
     */
    @GetMapping("/admin")
    public String index() {
        return "admin/index";
    }

    /**
     * 流水时间线入口辅助路由：导航页表单提交 uid 后重定向到用户流水页。
     *
     * @param uid 用户唯一标识
     * @return 重定向到用户流水页
     */
    @GetMapping("/admin/flows")
    public String flowsRedirect(@RequestParam Long uid) {
        return "redirect:/admin/accounts/" + uid + "/flows";
    }
}
