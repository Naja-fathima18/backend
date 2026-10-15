package com.app.backend.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.files.persistence.StoredFileRepository;
import com.app.backend.files.service.FilesProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
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

    @TempDir
    static Path storageDir;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StoredFileRepository repository;

    @Autowired
    private MultipartProperties multipartProperties;

    @Autowired
    private FilesProperties filesProperties;

    @DynamicPropertySource
    static void storageDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.files.storage-dir", () -> storageDir.toString());
    }

    @Test
    void uploadsListsDownloadsAndDeletesEachAllowedType() throws IOException {
        List<Upload> uploads = List.of(
                new Upload("photo.jpg", FileSamples.jpeg(4096), "image/jpeg"),
                new Upload("diagram.png", FileSamples.png(2048), "image/png"),
                new Upload("report.pdf", FileSamples.pdf(1024), "application/pdf"));

        for (Upload upload : uploads) {
            List<String> filesBefore = storedFileNames();

            ResponseEntity<JsonNode> created = upload(upload.name(), upload.content());

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(storedFileNames()).hasSize(filesBefore.size() + 1);
            JsonNode body = created.getBody();
            long id = body.get("id").asLong();
            assertThat(created.getHeaders().getLocation()).hasPath("/api/files/" + id);
            assertThat(body.get("originalName").asText()).isEqualTo(upload.name());
            assertThat(body.get("contentType").asText()).isEqualTo(upload.mediaType());
            assertThat(body.get("size").asLong()).isEqualTo(upload.content().length);
            assertThat(body.get("uploadedAt").asText()).isNotBlank();
            assertThat(listedIds()).contains(id);

            ResponseEntity<byte[]> download = restTemplate.getForEntity("/api/files/" + id, byte[].class);

            assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(download.getBody()).isEqualTo(upload.content());
            assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(upload.mediaType()));
            assertThat(download.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo(upload.name());
            assertThat(download.getHeaders().getContentDisposition().isAttachment())
                    .isTrue();

            ResponseEntity<Void> deleted = restTemplate.exchange(
                    URI.create("/api/files/" + id).toString(), HttpMethod.DELETE, null, Void.class);

            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(repository.existsById(id)).isFalse();
            assertThat(storedFileNames()).containsExactlyInAnyOrderElementsOf(filesBefore);
            assertThat(restTemplate
                            .getForEntity("/api/files/" + id, String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(restTemplate
                            .exchange("/api/files/" + id, HttpMethod.DELETE, null, String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void listsNewestFirst() {
        long older =
                upload("older.png", FileSamples.png(64)).getBody().get("id").asLong();
        long newer =
                upload("newer.png", FileSamples.png(64)).getBody().get("id").asLong();

        List<Long> ids = listedIds();

        assertThat(ids.indexOf(newer)).isLessThan(ids.indexOf(older));
    }

    @Test
    void acceptsExactlyFiveMegabytes() {
        ResponseEntity<JsonNode> response = upload("max.pdf", FileSamples.pdf(FileSamples.FIVE_MB));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("size").asLong()).isEqualTo(FileSamples.FIVE_MB);
    }

    @Test
    void rejectsOneByteOverFiveMegabytesWith413() {
        ResponseEntity<JsonNode> response = upload("over.pdf", FileSamples.pdf(FileSamples.FIVE_MB + 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("File exceeds the maximum size of 5242880 bytes");
    }

    @Test
    void multipartLimitsLeaveRoomForTheServiceToRejectFilesJustOverFiveMegabytes() {
        long fiveMegabytes = FileSamples.FIVE_MB;

        assertThat(multipartProperties.getMaxFileSize().toBytes()).isGreaterThan(fiveMegabytes);
        assertThat(multipartProperties.getMaxRequestSize().toBytes())
                .isGreaterThan(multipartProperties.getMaxFileSize().toBytes());
        assertThat(filesProperties.maxSize().toBytes()).isEqualTo(fiveMegabytes);
    }

    @Test
    void rejectsExecutableRenamedToPngWith415() {
        ResponseEntity<JsonNode> response = upload("photo.png", FileSamples.windowsExecutable());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Only JPEG, PNG and PDF files are allowed");
    }

    @Test
    void rejectsPdfNamedAsPngWith415() {
        ResponseEntity<JsonNode> response = upload("photo.png", FileSamples.pdf(100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("File extension does not match its content, which is PDF");
    }

    @Test
    void rejectsMissingPartWith400() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("other", part("other", "x.png", FileSamples.png(10)));

        ResponseEntity<JsonNode> response = post(form);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").get(0).get("field").asText())
                .isEqualTo("file");
    }

    @Test
    void rejectsEmptyFileWith400() {
        ResponseEntity<JsonNode> response = upload("empty.png", new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").get(0).get("message").asText())
                .isEqualTo("must not be empty");
    }

    @Test
    void traversalNamesNeverLeaveTheStorageDirectory() throws IOException {
        byte[] png = FileSamples.png(128);

        ResponseEntity<JsonNode> unix = upload("../../etc/passwd.png", png);
        ResponseEntity<JsonNode> windows = upload("..\\evil.pdf", FileSamples.pdf(128));

        assertThat(unix.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(windows.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(unix.getBody().get("originalName").asText()).isEqualTo("passwd.png");
        assertThat(windows.getBody().get("originalName").asText()).isEqualTo("evil.pdf");
        assertThat(storedFileNames()).allMatch(name -> name.matches("[0-9a-f-]{36}\\.(png|pdf|jpg)"));
        assertThat(Files.exists(storageDir.getParent().resolve("evil.pdf"))).isFalse();
        assertThat(Files.exists(storageDir.getParent().resolve("passwd.png"))).isFalse();
        ResponseEntity<byte[]> download = restTemplate.getForEntity(
                "/api/files/" + unix.getBody().get("id").asLong(), byte[].class);
        assertThat(download.getBody()).isEqualTo(png);
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("passwd.png");
    }

    private ResponseEntity<JsonNode> upload(String fileName, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", part("file", fileName, content));
        return post(form);
    }

    private ResponseEntity<JsonNode> post(MultiValueMap<String, Object> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForEntity("/api/files", new HttpEntity<>(form, headers), JsonNode.class);
    }

    private List<Long> listedIds() {
        JsonNode page = restTemplate.getForObject("/api/files?size=100", JsonNode.class);
        return page.get("content").findValues("id").stream()
                .map(JsonNode::asLong)
                .toList();
    }

    private static List<String> storedFileNames() throws IOException {
        if (!Files.exists(storageDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(storageDir)) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static HttpEntity<ByteArrayResource> part(String partName, String fileName, byte[] content) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        partHeaders.setContentDispositionFormData(partName, fileName);
        return new HttpEntity<>(new ByteArrayResource(content), partHeaders);
    }

    private record Upload(String name, byte[] content, String mediaType) {}
}
