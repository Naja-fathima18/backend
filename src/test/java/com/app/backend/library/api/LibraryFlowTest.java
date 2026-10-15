package com.app.backend.library.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.library.TestIsbns;
import com.jayway.jsonpath.JsonPath;
import java.time.Year;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class LibraryFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void borrowedBookCannotBeBorrowedAgainOrDeletedUntilReturned() throws Exception {
        long id = createBook("Flow " + UUID.randomUUID(), "Author", TestIsbns.randomIsbn13(), 1999);

        mockMvc.perform(borrow(id, "Alice"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(id))
                .andExpect(jsonPath("$.memberName").value("Alice"));
        mockMvc.perform(borrow(id, "Bob"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book " + id + " is already borrowed"));
        mockMvc.perform(delete("/api/books/{id}", id)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/books/{id}/return", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberName").value("Alice"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());
        mockMvc.perform(post("/api/books/{id}/return", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book " + id + " is not borrowed"));
        mockMvc.perform(delete("/api/books/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/books/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateIsbnOnCreateAndUpdateComparingNormalizedForm() throws Exception {
        String isbn = TestIsbns.randomIsbn13();
        String hyphenated = isbn.substring(0, 3) + "-" + isbn.substring(3);
        long first = createBook("First", "Author", isbn, 2000);
        long second = createBook("Second", "Author", TestIsbns.randomIsbn13(), 2000);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Dup", "A", hyphenated, 2000)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/books/{id}", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Second", "Author", hyphenated, 2000)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/books/{id}", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("First edited", "Author", hyphenated, 2001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First edited"))
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rejectsPublishedYearInTheFuture() throws Exception {
        int nextYear = Year.now().getValue() + 1;

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Future", "Author", TestIsbns.randomIsbn13(), nextYear)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("publishedYear"));
    }

    @Test
    void searchesTitleOrAuthorCaseInsensitivelyWithPaging() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        createBook("The " + marker.toUpperCase() + " Saga", "Someone", TestIsbns.randomIsbn13(), 2010);
        createBook("Unrelated", "Writer " + marker, TestIsbns.randomIsbn13(), 2011);
        createBook("Another unrelated", "Nobody", TestIsbns.randomIsbn13(), 2012);

        mockMvc.perform(get("/api/books").param("q", marker).param("size", "1").param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("The " + marker.toUpperCase() + " Saga"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/books")
                        .param("q", marker)
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Unrelated"));
    }

    @Test
    void rejectsSortingByUnknownProperty() throws Exception {
        mockMvc.perform(get("/api/books").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void createReturnsLocationOfTheNewBook() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Located", "Author", TestIsbns.randomIsbn13(), 2005)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/books/\\d+")));
    }

    @Test
    void borrowingUnknownBookReturnsNotFound() throws Exception {
        mockMvc.perform(borrow(Long.MAX_VALUE, "Alice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Book " + Long.MAX_VALUE + " not found"));
    }

    private long createBook(String title, String author, String isbn, int year) throws Exception {
        String response = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(title, author, isbn, year)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static RequestBuilder borrow(long id, String member) {
        return post("/api/books/{id}/borrow", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberName\":\"" + member + "\"}");
    }

    private static String json(String title, String author, String isbn, int year) {
        return "{\"title\":\"%s\",\"author\":\"%s\",\"isbn\":\"%s\",\"publishedYear\":%d}"
                .formatted(title, author, isbn, year);
    }
}
