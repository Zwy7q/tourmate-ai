package com.tourmate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class ChatHttpTest {
    @Autowired MockMvc mvc;

    @Test void savesAnswerAndUsesHistoryForFollowup() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"故宫门票多少钱？\",\"conversationId\":\"http-followup\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"那学生票呢？\",\"conversationId\":\"http-followup\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.intent").value("ATTRACTION_INFO"))
                .andExpect(jsonPath("$.citations").isNotEmpty());
        mvc.perform(get("/api/conversations/http-followup/messages"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }

    @Test void streamsAllEventTypes() throws Exception {
        var result = mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"故宫几点开放？\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10000);
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().string(containsString("event:meta")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:done")));
    }
}
