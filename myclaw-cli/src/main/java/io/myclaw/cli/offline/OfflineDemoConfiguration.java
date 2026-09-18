package io.myclaw.cli.offline;

import io.myclaw.core.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@code offline} profile：用一个本地演示模型替换掉真实的 OpenAI 兼容模型。
 *
 * <p>因为 {@code MyClawAutoConfiguration} 里的 ChatModel Bean 标了
 * {@code @ConditionalOnMissingBean(ChatModel.class)}，这里定义的 Bean 会自动生效，
 * 这也是"如何替换框架默认实现"的示例。
 */
@Configuration
@Profile("offline")
public class OfflineDemoConfiguration {

    @Bean
    public ChatModel offlineChatModel() {
        return new OfflineChatModel();
    }
}
