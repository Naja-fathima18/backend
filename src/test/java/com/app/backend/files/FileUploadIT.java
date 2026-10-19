package com.app.backend.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.persistence.StoredFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileUploadIT {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    @TempDir
    static Path tempDir;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoredFileRepository repository;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.files.storage-dir", () -> storageRoot().toString());
    }

    @Test
    void uploadsListsDownloadsAndDeletesPng() throws IOException {
        ResponseEntity<String> created = upload("holiday.png", MediaType.IMAGE_PNG, FileSamples.PNG);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode metadata = objectMapper.readTree(created.getBody());
        String id = metadata.get("id").asText();
        assertThat(created.getHeaders().getLocation()).hasPath("/api/files/" + id);
        assertThat(metadata.get("originalName").asText()).isEqualTo("holiday.png");
        assertThat(metadata.get("contentType").asText()).isEqualTo("image/png");
        assertThat(metadata.get("size").asLong()).isEqualTo(FileSamples.PNG.length);
        assertThat(metadata.get("uploadedAt").asText()).isNotBlank();
        Path content = contentPathOf(id);
        assertThat(content).exists();

        ResponseEntity<String> fetched =
                restTemplate.getForEntity(created.getHeaders().getLocation(), String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(fetched.getBody()).get("id").asText()).isEqualTo(id);

        ResponseEntity<byte[]> download = download(id);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(download.getBody()).isEqualTo(FileSamples.PNG);
        ContentDisposition disposition = download.getHeaders().getContentDisposition();
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo("holiday.png");

        ResponseEntity<Void> deleted = delete(id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(content).doesNotExist();
        assertThat(repository.findById(UUID.fromString(id))).isEmpty();
        assertThat(restTemplate.getForEntity("/api/files/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void acceptsJpegAndPdfDetectedFromContentRegardlessOfDeclaredType() throws IOException {
        ResponseEntity<String> jpeg = upload("photo.jpg", MediaType.APPLICATION_OCTET_STREAM, FileSamples.JPEG);
        ResponseEntity<String> pdf = upload("report.pdf", MediaType.TEXT_PLAIN, FileSamples.PDF);

        assertThat(jpeg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(jpeg.getBody()).get("contentType").asText())
                .isEqualTo("image/jpeg");
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(pdf.getBody()).get("contentType").asText())
                .isEqualTo("application/pdf");
    }

    @Test
    void downloadKeepsNonAsciiAndQuotedOriginalName() throws IOException {
        String originalName = "résumé \"final\".pdf";
        String id = idOf(upload(originalName, MediaType.APPLICATION_PDF, FileSamples.PDF));

        ResponseEntity<byte[]> download = download(id);

        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(originalName);
        assertThat(download.getBody()).isEqualTo(FileSamples.PDF);
    }

    @Test
    void listsNewestFirstWithMetadata() throws IOException {
        String older = idOf(upload("older.png", MediaType.IMAGE_PNG, FileSamples.PNG));
        String newer = idOf(upload("newer.pdf", MediaType.APPLICATION_PDF, FileSamples.PDF));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/files?size=100", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = objectMapper.readTree(response.getBody());
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).containsSubsequence(newer, older);
        JsonNode newest = page.get("content").get(ids.indexOf(newer));
        assertThat(newest.get("originalName").asText()).isEqualTo("newer.pdf");
        assertThat(newest.get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(newest.get("size").asLong()).isEqualTo(FileSamples.PDF.length);
        assertThat(newest.get("uploadedAt").asText()).isNotBlank();
        assertThat(page.get("size").asInt()).isEqualTo(100);
    }

    @Test
    void rejectsFileOverFiveMegabytesWith413() throws IOException {
        long before = storedFiles().size();

        ResponseEntity<String> response = upload("big.png", MediaType.IMAGE_PNG, FileSamples.pngOfSize(FIVE_MB + 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .isEqualTo("File exceeds the maximum allowed size of 5 MB");
        assertThat(storedFiles()).hasSize((int) before);
    }

    @Test
    void rejectsExecutableRenamedToPngWith415() throws IOException {
        ResponseEntity<String> response = upload("photo.png", MediaType.IMAGE_PNG, FileSamples.EXE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .isEqualTo("Unsupported file type: only JPEG, PNG and PDF files are allowed");
    }

    @Test
    void rejectsTextFileWith415() {
        ResponseEntity<String> response = upload("notes.txt", MediaType.TEXT_PLAIN, FileSamples.TEXT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsPdfNamedAsPngWith415() throws IOException {
        ResponseEntity<String> response = upload("photo.png", MediaType.IMAGE_PNG, FileSamples.PDF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .contains("does not match");
    }

    @Test
    void rejectsEmptyFileWith400() {
        ResponseEntity<String> response = upload("empty.png", MediaType.IMAGE_PNG, new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingFilePartWith400() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("other", "value");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/files", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"field\":\"file\"");
    }

    @Test
    void traversalNameNeverWritesOrReadsOutsideStorageRoot() throws IOException {
        ResponseEntity<String> created = upload("../../evil.png", MediaType.IMAGE_PNG, FileSamples.PNG);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = idOf(created);
        assertThat(objectMapper.readTree(created.getBody()).get("originalName").asText())
                .isEqualTo("evil.png");
        try (Stream<Path> everything = Files.walk(tempDir)) {
            List<Path> files = everything.filter(Files::isRegularFile).toList();
            assertThat(files).allSatisfy(file -> {
                assertThat(file.getParent()).isEqualTo(storageRoot());
                assertThat(UUID.fromString(file.getFileName().toString())).isNotNull();
            });
            assertThat(files).noneMatch(file -> file.getFileName().toString().contains("evil"));
        }
        assertThat(download(id).getBody()).isEqualTo(FileSamples.PNG);
    }

    @Test
    void downloadReturns404WhenStoredContentIsMissing() throws IOException {
        String id = idOf(upload("gone.png", MediaType.IMAGE_PNG, FileSamples.PNG));
        Files.delete(contentPathOf(id));

        ResponseEntity<byte[]> response = download(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownIdReturns404() {
        String id = UUID.randomUUID().toString();

        assertThat(download(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Path storageRoot() {
        return tempDir.resolve("store").resolve("files").toAbsolutePath().normalize();
    }

    private Path contentPathOf(String id) {
        StoredFile file = repository.findById(UUID.fromString(id)).orElseThrow();
        return storageRoot().resolve(file.getStorageKey().toString());
    }

    private List<Path> storedFiles() throws IOException {
        if (!Files.isDirectory(storageRoot())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(storageRoot())) {
            return files.toList();
        }
    }

    private String idOf(ResponseEntity<String> response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("id").asText();
    }

    private ResponseEntity<String> upload(String filename, MediaType partType, byte[] content) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(partType);
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name("file")
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(content), partHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForEntity("/api/files", new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> download(String id) {
        return restTemplate.getForEntity("/api/files/" + id + "/download", byte[].class);
    }

    private ResponseEntity<Void> delete(String id) {
        return restTemplate.exchange("/api/files/" + id, HttpMethod.DELETE, null, Void.class);
    }
}
