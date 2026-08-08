package io.github.open55.mockupstream.state;

import lombok.Data;

/**
 * 操作日志条目（内存模型，不可变）。
 * <p>
 * 记录一次用户操作或内部处理步骤的完整信息：谁（业务身份）、做了什么、
 * 调用了哪个端点、OTX 如何响应。替代原自动演绎的控制台日志，
 * 在个人中心页面倒序展示，让每个动作的业务含义清晰可见。
 */
@Data
public class OperationLogEntry {

    /**
     * 操作发生时间戳（毫秒），用于排序与展示
     */
    private final long timestamp;

    /**
     * 业务身份前缀：说明操作主体与场景（如"👤 用户 #100123 充值"、"⚙ 内部处理"）
     */
    private final String actor;

    /**
     * 动作描述：现实业务含义（如"链上确认达标，通知 OTX 充值入账"）
     */
    private final String description;

    /**
     * HTTP 端点：实际调用的 OTX 接口（如 "POST /deposit"；无 HTTP 调用时为 "-"）
     */
    private final String endpoint;

    /**
     * OTX 响应摘要（Result 的 code/message 或 data 要点；无响应时为 "-"）
     */
    private final String response;
}
