package com.tourmate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeUploadTest {
    @Autowired MockMvc mvc;

    @Test void uploadsMarkdownAndRetrievesItsPolicy() throws Exception {
        var file = new MockMultipartFile("file", "海洋馆.md", "text/markdown",
                "# 海洋馆\n海洋馆潜水票只能提前两日退订。".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/knowledge/upload").file(file).param("category", "refund-policy"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunks").value(1));
        mvc.perform(get("/api/knowledge/search").param("query", "海洋馆潜水"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].document.content", containsString("提前两日")));
    }

    @Test void refusesEmptyFile() throws Exception {
        mvc.perform(multipart("/api/knowledge/upload").file(new MockMultipartFile("file", "empty.md", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest());
    }
}
