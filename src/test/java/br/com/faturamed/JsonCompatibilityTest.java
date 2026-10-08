package br.com.faturamed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JsonCompatibilityTest.JsonController.class)
@Import(JsonCompatibilityTest.JsonController.class)
class JsonCompatibilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void preservesJackson2DatesAndJsonNodesInHttpRequestsAndResponses() throws Exception {
        String body = """
                {"data":"2026-10-08","dadosOriginais":{"paciente":"Teste","quantidade":2}}
                """;
        String response = mvc.perform(post("/test/json")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(json.readTree(body), json.readTree(response));
        assertEquals(LocalDate.of(2026, 10, 8), json.readValue(response, Payload.class).data());
    }

    public record Payload(LocalDate data, JsonNode dadosOriginais) {}

    @RestController
    static class JsonController {
        @PostMapping("/test/json")
        Payload echo(@RequestBody Payload payload) {
            return payload;
        }
    }
}
