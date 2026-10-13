package com.tourmate;

import com.tourmate.conversation.ConversationMessage;
import com.tourmate.conversation.InMemoryConversationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired InMemoryConversationStore store;

    @Test void returnsRecentConversationMessages() throws Exception {
        store.append("demo", new ConversationMessage("user", "故宫开放吗？"));
        mvc.perform(get("/api/conversations/demo/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("故宫开放吗？"));
    }
}
