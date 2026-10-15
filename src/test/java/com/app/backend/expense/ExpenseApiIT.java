package com.app.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExpenseApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createsUpdatesSummarizesAndDeletesExpense() {
        ResponseEntity<Map> created = post("{\"amount\":0.10,\"category\":\"FOOD\",\"date\":\"2031-03-01\"}");
        post("{\"amount\":\"0.20\",\"category\":\"food\",\"date\":\"2031-03-31\"}");
        post("{\"amount\":99.00,\"category\":\"FOOD\",\"date\":\"2031-04-01\"}");
        URI location = created.getHeaders().getLocation();

        ResponseEntity<Map> fetched = restTemplate.getForEntity(location, Map.class);
        ResponseEntity<Map> updated = restTemplate.exchange(
                location,
                HttpMethod.PUT,
                json("{\"amount\":0.1,\"category\":\"FOOD\",\"date\":\"2031-03-01\",\"note\":\"tea\"}"),
                Map.class);
        ResponseEntity<String> summary = restTemplate.getForEntity("/api/expenses/summary?month=2031-03", String.class);
        ResponseEntity<Map> list = restTemplate.getForEntity(
                "/api/expenses?from=2031-03-01&to=2031-03-31&category=FOOD&size=10", Map.class);
        ResponseEntity<Void> deleted = restTemplate.exchange(location, HttpMethod.DELETE, null, Void.class);
        ResponseEntity<String> afterDelete = restTemplate.getForEntity(location, String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(location).isNotNull();
        assertThat(location.getPath())
                .isEqualTo("/api/expenses/" + created.getBody().get("id"));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).containsEntry("amount", "0.10");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("note", "tea").containsEntry("amount", "0.10");
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody())
                .isEqualTo("{\"month\":\"2031-03\",\"totals\":{\"FOOD\":\"0.30\",\"TRAVEL\":\"0.00\","
                        + "\"BILLS\":\"0.00\",\"OTHER\":\"0.00\"},\"overallTotal\":\"0.30\"}");
        assertThat(list.getBody()).containsEntry("totalElements", 2);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsAmountWithThreeDecimalsWithoutPersisting() {
        ResponseEntity<Map> response = post("{\"amount\":10.005,\"category\":\"BILLS\",\"date\":\"2032-05-10\"}");

        ResponseEntity<Map> summary = restTemplate.getForEntity("/api/expenses/summary?month=2032-05", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(summary.getBody()).containsEntry("overallTotal", "0.00");
    }

    private ResponseEntity<Map> post(String body) {
        return restTemplate.postForEntity("/api/expenses", json(body), Map.class);
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
