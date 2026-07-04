package io.github.open55.otx.application.account.dto.request;

/**
 * 创建账户请求。
 *
 * @param uid 用户唯一标识
 */
public record CreateAccountRequest(
        Long uid
) {
}
