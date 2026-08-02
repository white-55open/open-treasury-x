package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
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
 * 提现 REST 控制器，提供链下账务与链上广播编排两组入口。
 * <p>
 * 链下账务端点（冻结、结算、解冻）统一返回 Result&lt;String&gt;（data 为业务流水号）；
 * 链上广播编排端点（广播、确认结算、取消、状态查询）以提现请求记录为状态载体，
 * 返回结构化响应 DTO。各端点均以业务号（bizNo）保证幂等。
 */
@Tag(name = "withdraw", description = "提现模块 — 冻结、结算、解冻与链上广播编排")
@RestController
@RequestMapping("/withdraw")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawAppService withdrawAppService;

    /**
     * 提现冻结：申请提现时锁定资金。
     * <p>
     * 将申请金额从可用余额转入冻结余额，记录 FREEZE 流水并过账冻结凭证。
     *
     * @param request 提现请求体，包含 uid、bizNo、amount、currency
     * @return 统一响应体，data 为业务流水号
     */
    @Operation(summary = "提现冻结", description = "申请提现时锁定资金，可用余额转入冻结余额，幂等保证相同 bizNo 重复请求返回相同结果")
    @PostMapping("/freeze")
    public Result<String> freeze(@RequestBody WithdrawRequestDTO request) {
        return Result.success(withdrawAppService.freeze(request));
    }

    /**
     * 提现结算：审批通过后从冻结余额扣减。
     * <p>
     * 仅校验冻结余额充足性，扣减后资金进入提现在途，记录 WITHDRAW 流水并过账结算凭证。
     *
     * @param request 提现请求体，包含 uid、bizNo、amount、currency
     * @return 统一响应体，data 为业务流水号
     */
    @Operation(summary = "提现结算", description = "审批通过后从冻结余额扣减，资金进入提现在途，幂等保证相同 bizNo 重复请求返回相同结果")
    @PostMapping("/settle")
    public Result<String> settle(@RequestBody WithdrawRequestDTO request) {
        return Result.success(withdrawAppService.withdraw(request));
    }

    /**
     * 提现解冻：取消或驳回时释放冻结资金。
     * <p>
     * 将冻结余额退回可用余额，记录 UNFREEZE 流水并过账解冻凭证，不冲销原冻结凭证。
     *
     * @param request 提现请求体，包含 uid、bizNo、amount、currency
     * @return 统一响应体，data 为业务流水号
     */
    @Operation(summary = "提现解冻", description = "取消或驳回提现时释放冻结资金退回可用余额，幂等保证相同 bizNo 重复请求返回相同结果")
    @PostMapping("/unfreeze")
    public Result<String> unfreeze(@RequestBody WithdrawRequestDTO request) {
        return Result.success(withdrawAppService.unfreeze(request));
    }

    /**
     * 提现广播：构造交易、签名并提交链上，为提现请求留痕。
     * <p>
     * 业务流程：保存 PENDING 提现请求 → 事务外查询 nonce、签名、广播 →
     * 广播成功置 BROADCASTED 并创建 DRAFT 凭证；广播失败置 FAILED 且不创建凭证。
     * 幂等保证：相同 bizNo 重复请求返回已有请求的广播结果。
     *
     * @param request 提现请求体，包含 uid、bizNo、amount、currency、chainId、toAddress
     * @return 统一响应体，data 为广播结果（bizNo、txHash、status）
     */
    @Operation(summary = "提现广播", description = "构造交易并签名后提交链上，广播成功创建草稿凭证，幂等保证相同 bizNo 重复请求返回已有广播结果")
    @PostMapping("/broadcast")
    public Result<WithdrawBroadcastResponseDTO> broadcast(@RequestBody WithdrawRequestDTO request) {
        return Result.success(withdrawAppService.broadcast(request));
    }

    /**
     * 提现确认结算：链上确认达标后完成冻结资金扣减与凭证过账。
     * <p>
     * 业务流程：加载提现请求 → 查询链上回执判断交易成功 → 确认数达标后扣减冻结余额、
     * 落 WITHDRAW 流水并置 SETTLED，随后过账 DRAFT 凭证；未确认抛 TX_NOT_CONFIRMED_YET。
     *
     * @param bizNo 业务流水号，路径变量，用作幂等键
     * @return 统一响应体，data 为结算结果（bizNo、status、journalStatus）
     */
    @Operation(summary = "提现确认结算", description = "链上确认数达标后扣减冻结余额、落 WITHDRAW 流水并过账草稿凭证")
    @PostMapping("/{bizNo}/confirm-settle")
    public Result<WithdrawSettleResponseDTO> confirmSettle(@PathVariable String bizNo) {
        return Result.success(withdrawAppService.confirmAndSettle(bizNo));
    }

    /**
     * 提现取消：解冻已冻结资金并置请求为 CANCELLED。
     * <p>
     * 业务场景：风控拒绝、审核不通过或用户主动取消。
     * 解冻资金并落 UNFREEZE 流水；已取消请求幂等返回，已结算请求不可取消。
     *
     * @param bizNo 业务流水号，路径变量，用作幂等键
     * @return 统一响应体，data 为取消后的提现请求状态
     */
    @Operation(summary = "提现取消", description = "风控拒绝或用户主动取消时解冻资金并置请求为 CANCELLED，已结算请求不可取消")
    @PostMapping("/{bizNo}/cancel")
    public Result<WithdrawStatusResponseDTO> cancelWithdraw(@PathVariable String bizNo) {
        return Result.success(withdrawAppService.cancelWithdraw(bizNo));
    }

    /**
     * 提现状态查询：按业务流水号查询提现请求的当前状态与链上交易哈希。
     *
     * @param bizNo 业务流水号，路径变量
     * @return 统一响应体，data 为提现请求状态（bizNo、status、txHash、amount、currency）
     */
    @Operation(summary = "提现状态查询", description = "按业务流水号查询提现请求的当前状态与链上交易哈希，用于跟踪结算进度")
    @GetMapping("/{bizNo}/status")
    public Result<WithdrawStatusResponseDTO> queryStatus(@PathVariable String bizNo) {
        return Result.success(withdrawAppService.queryStatus(bizNo));
    }
}
