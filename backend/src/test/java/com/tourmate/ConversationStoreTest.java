package com.tourmate;

import com.tourmate.conversation.InMemoryConversationStore;
import com.tourmate.conversation.ConversationMessage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationStoreTest {
    @Test void keepsOnlyRecentMessagesWithinWindow() {
        var store = new InMemoryConversationStore(2);
        store.append("c1", new ConversationMessage("user", "故宫开放吗？"));
        store.append("c1", new ConversationMessage("assistant", "开放时间为 8:30-16:10。"));
        store.append("c1", new ConversationMessage("user", "那学生票呢？"));
        assertThat(store.recent("c1")).extracting(ConversationMessage::content)
                .containsExactly("开放时间为 8:30-16:10。", "那学生票呢？");
    }

    @Test void separatesConversations() {
        var store = new InMemoryConversationStore(4);
        store.append("a", new ConversationMessage("user", "北京天气"));
        store.append("b", new ConversationMessage("user", "上海天气"));
        assertThat(store.recent("a")).hasSize(1);
        assertThat(store.recent("a").get(0).content()).isEqualTo("北京天气");
    }
}
