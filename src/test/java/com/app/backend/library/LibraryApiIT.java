package com.app.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.library.persistence.BookRepository;
import com.app.backend.library.persistence.LoanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @AfterEach
    void cleanUp() {
        loanRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
    }

    @Test
    void borrowReturnAndBorrowAgainKeepsLoanHistory() {
        URI location = createBook("Dune", "Frank Herbert", " 978-0-441-01359-3 ");
        ResponseEntity<JsonNode> book = rest.getForEntity(location, JsonNode.class);
        long id = book.getBody().get("id").asLong();
        assertThat(book.getBody().get("isbn").asText()).isEqualTo("9780441013593");
        assertThat(book.getBody().get("available").asBoolean()).isTrue();

        ResponseEntity<JsonNode> firstLoan = borrow(id, "member-1");
        ResponseEntity<JsonNode> secondBorrow = borrow(id, "member-2");
        ResponseEntity<JsonNode> deleteWhileBorrowed =
                rest.exchange("/api/books/" + id, HttpMethod.DELETE, null, JsonNode.class);
        ResponseEntity<JsonNode> returned = rest.postForEntity("/api/books/" + id + "/return", null, JsonNode.class);
        ResponseEntity<JsonNode> secondReturn =
                rest.postForEntity("/api/books/" + id + "/return", null, JsonNode.class);
        ResponseEntity<JsonNode> borrowAgain = borrow(id, "member-2");

        assertThat(firstLoan.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstLoan.getBody().get("memberId").asText()).isEqualTo("member-1");
        assertThat(secondBorrow.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondBorrow.getBody().get("detail").asText()).isEqualTo("Book " + id + " is already borrowed");
        assertThat(deleteWhileBorrowed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(returned.getBody().get("id").asLong())
                .isEqualTo(firstLoan.getBody().get("id").asLong());
        assertThat(returned.getBody().get("returnedAt").isNull()).isFalse();
        assertThat(secondReturn.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(borrowAgain.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(loanRepository.countByBookId(id)).isEqualTo(2);
    }

    @Test
    void deletesAvailableBookWithLoanHistory() {
        URI location = createBook("Dune", "Frank Herbert", "9780441013593");
        long id = rest.getForObject(location, JsonNode.class).get("id").asLong();
        borrow(id, "member-1");
        rest.postForEntity("/api/books/" + id + "/return", null, JsonNode.class);

        ResponseEntity<Void> deleted = rest.exchange(location, HttpMethod.DELETE, null, Void.class);
        ResponseEntity<JsonNode> afterDelete = rest.getForEntity(location, JsonNode.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsDuplicateIsbnOnCreateAndUpdate() {
        createBook("Dune", "Frank Herbert", "9780441013593");
        URI other = createBook("Foundation", "Isaac Asimov", "9780553293357");

        ResponseEntity<JsonNode> duplicateCreate =
                rest.postForEntity("/api/books", bookBody("Copy", "Someone", "978-0441013593"), JsonNode.class);
        ResponseEntity<JsonNode> duplicateUpdate = rest.exchange(
                other,
                HttpMethod.PUT,
                new HttpEntity<>(bookBody("Foundation", "Isaac Asimov", "9780441013593")),
                JsonNode.class);

        assertThat(duplicateCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateKeepsOwnIsbnAndReplacesFields() {
        URI location = createBook("Dune", "Frank Herbert", "9780441013593");

        ResponseEntity<JsonNode> updated = rest.exchange(
                location,
                HttpMethod.PUT,
                new HttpEntity<>(bookBody("Dune (Deluxe)", "F. Herbert", "9780441013593")),
                JsonNode.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("title").asText()).isEqualTo("Dune (Deluxe)");
        assertThat(updated.getBody().get("author").asText()).isEqualTo("F. Herbert");
    }

    @Test
    void searchesCaseInsensitivelyWithPagination() {
        createBook("Dune", "Frank Herbert", "9780441013593");
        createBook("Dune Messiah", "Frank Herbert", "9780593098233");
        createBook("Foundation", "Isaac Asimov", "9780553293357");

        ResponseEntity<JsonNode> page =
                rest.getForEntity("/api/books?title=DUNE&author=herbert&size=1&page=1&sort=title", JsonNode.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody().get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.getBody().get("content").get(0).get("title").asText()).isEqualTo("Dune Messiah");
    }

    @Test
    void returnsNotFoundForUnknownBookOperations() {
        ResponseEntity<JsonNode> borrow = borrow(999_999L, "member-1");
        ResponseEntity<JsonNode> returned = rest.postForEntity("/api/books/999999/return", null, JsonNode.class);

        assertThat(borrow.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private URI createBook(String title, String author, String isbn) {
        ResponseEntity<JsonNode> response =
                rest.postForEntity("/api/books", bookBody(title, author, isbn), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getHeaders().getLocation();
    }

    private ResponseEntity<JsonNode> borrow(long id, String memberId) {
        return rest.postForEntity("/api/books/" + id + "/borrow", Map.of("memberId", memberId), JsonNode.class);
    }

    private static Map<String, Object> bookBody(String title, String author, String isbn) {
        return Map.of("title", title, "author", author, "isbn", isbn, "publishedYear", 1965);
    }
}
