package com.tourmate;

import com.tourmate.api.ItineraryController;
import com.tourmate.itinerary.*;
import com.tourmate.knowledge.InMemoryKnowledgeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItineraryController.class)
@Import(ItineraryHttpTest.Configuration.class)
class ItineraryHttpTest {
    @Autowired MockMvc mvc;
    @Test void collectsRequirementsAndReadsSavedState() throws Exception {
        mvc.perform(post("/api/itineraries/plan").contentType("application/json").content("{\"conversationId\":\"http-trip\",\"message\":\"去杭州\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("NEEDS_INFORMATION"))
                .andExpect(jsonPath("requirements.city").value("杭州"));
        mvc.perform(get("/api/itineraries/http-trip")).andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
        mvc.perform(get("/api/itineraries/unknown")).andExpect(status().isNotFound());
    }
    @Test void validatesRequestAndSanitizesGenerationFailure() throws Exception {
        mvc.perform(post("/api/itineraries/plan").contentType("application/json").content("{\"conversationId\":\"\",\"message\":\"去杭州\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/itineraries/plan").contentType("application/json").content("{\"conversationId\":\"failed\",\"message\":\"fail\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("error").value("行程生成暂时不可用，请检查模型配置或调整要求后重试"));
        mvc.perform(get("/api/itineraries/failed")).andExpect(status().isNotFound());
    }
    @Test void explainsWhenDeepSeekPlanningIsDisabled() throws Exception {
        mvc.perform(post("/api/itineraries/plan").contentType("application/json").content("{\"conversationId\":\"offline\",\"message\":\"disabled\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("error").value("行程规划未启用，请配置DeepSeek并启用deepseek profile"));
    }
    @TestConfiguration static class Configuration {
        @Bean ItineraryService service() {
            var model = new ItineraryModel() {
                public TripRequirements extract(String message, TripRequirements current) {
                    if (message.equals("fail")) throw new IllegalStateException("private provider details");
                    if (message.equals("disabled")) return new UnavailableItineraryModel().extract(message, current);
                    return new TripRequirements("杭州", null, null, null, null, null);
                }
                public List<List<String>> schedule(TripRequirements r, List<AttractionCatalog.Attraction> a, List<ItineraryWeather.Forecast> w) { throw new AssertionError("不应生成行程"); }
            };
            return new ItineraryService(model, new AttractionCatalog(new InMemoryKnowledgeRepository()), r -> List.of(), new InMemoryItineraryStore());
        }
    }
}
