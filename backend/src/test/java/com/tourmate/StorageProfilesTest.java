package com.tourmate;

import com.tourmate.knowledge.*;
import com.tourmate.conversation.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class StorageProfilesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> org.mockito.Mockito.mock(JdbcTemplate.class))
            .withBean(StringRedisTemplate.class, () -> org.mockito.Mockito.mock(StringRedisTemplate.class))
            .withUserConfiguration(EmbeddingClient.class, InMemoryKnowledgeRepository.class, JdbcKnowledgeRepository.class,
                    InMemoryConversationStore.class, RedisConversationStore.class);

    @Test void productionProfilesSelectExactlyOneStorageImplementation() {
        runner.withPropertyValues("spring.profiles.active=postgres,redis").run(context -> {
            assertThat(context).hasSingleBean(KnowledgeRepository.class);
            assertThat(context).hasSingleBean(ConversationStore.class);
            assertThat(context.getBean(KnowledgeRepository.class)).isInstanceOf(JdbcKnowledgeRepository.class);
            assertThat(context.getBean(ConversationStore.class)).isInstanceOf(RedisConversationStore.class);
        });
    }

    @Test void defaultProfileKeepsOfflineAdapters() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(KnowledgeRepository.class);
            assertThat(context).hasSingleBean(ConversationStore.class);
            assertThat(context.getBean(KnowledgeRepository.class)).isInstanceOf(InMemoryKnowledgeRepository.class);
        });
    }
}
