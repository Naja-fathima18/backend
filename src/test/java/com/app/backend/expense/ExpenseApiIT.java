package com.app.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createsReadsSummarizesUpdatesAndDeletesExpense() {
        ResponseEntity<JsonNode> created = restTemplate.postForEntity(
                "/api/expenses", Map.of("amount", "0.10", "category", "food", "date", "2032-02-29"), JsonNode.class);
        restTemplate.postForEntity(
                "/api/expenses", Map.of("amount", "0.20", "category", "FOOD", "date", "2032-02-01"), JsonNode.class);
        URI location = created.getHeaders().getLocation();

        ResponseEntity<JsonNode> fetched = restTemplate.getForEntity(location, JsonNode.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/expenses/summary?month=2032-02", String.class);
        ResponseEntity<JsonNode> updated = restTemplate.exchange(
                location,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("amount", "1.00", "category", "OTHER", "date", "2032-03-01")),
                JsonNode.class);
        ResponseEntity<Void> deleted = restTemplate.exchange(location, HttpMethod.DELETE, null, Void.class);
        ResponseEntity<String> afterDelete = restTemplate.getForEntity(location, String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(location).isNotNull();
        assertThat(fetched.getBody().get("amount").decimalValue()).isEqualByComparingTo("0.10");
        assertThat(fetched.getBody().get("category").asText()).isEqualTo("FOOD");
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody())
                .contains("\"month\":\"2032-02\"")
                .contains("\"totals\":{\"FOOD\":0.30,\"TRAVEL\":0.00,\"BILLS\":0.00,\"OTHER\":0.00}")
                .contains("\"total\":0.30");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("amount").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(updated.getBody().get("category").asText()).isEqualTo("OTHER");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
