package io.github.open55.mockupstream.scenario;

/**
 * 故事线执行结果（供演示总结统计）。
 *
 * @param storyName    故事线名称（如"充值故事"）
 * @param totalSteps   总步骤数
 * @param successSteps 成功步骤数
 */
public record StoryResult(String storyName, int totalSteps, int successSteps) {

    /**
     * 失败步骤数（总步骤 - 成功步骤）。
     *
     * @return 失败步骤数
     */
    public int failedSteps() {
        return totalSteps - successSteps;
    }
}
