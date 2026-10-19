package com.app.backend.files.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.FileSamples;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.service.FileDownload;
import com.app.backend.files.service.FileService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-10-19T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void uploadReturnsCreatedWithLocationAndMetadata() throws Exception {
        StoredFile saved = storedFile("photo.png", "image/png", FileSamples.PNG.length);
        when(fileService.upload(any())).thenReturn(saved);

        mockMvc.perform(multipart("/api/files").file(filePart("photo.png", FileSamples.PNG)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/files/" + saved.getId()))
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.originalName").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(FileSamples.PNG.length))
                .andExpect(jsonPath("$.uploadedAt").value("2026-10-19T10:15:30Z"));
    }

    @Test
    void uploadPassesNullWhenFilePartIsMissing() throws Exception {
        when(fileService.upload(null)).thenThrow(new BadRequestException("file", "must not be empty"));

        mockMvc.perform(multipart("/api/files").param("other", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void uploadRejectsNonMultipartRequestWith415() throws Exception {
        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadMapsUnsupportedTypeToProblemDetail415() throws Exception {
        when(fileService.upload(any())).thenThrow(UnsupportedFileTypeException.notAllowed());

        mockMvc.perform(multipart("/api/files").file(filePart("photo.png", FileSamples.EXE)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(
                        jsonPath("$.detail").value("Unsupported file type: only JPEG, PNG and PDF files are allowed"));
    }

    @Test
    void uploadMapsTooLargeToProblemDetail413() throws Exception {
        when(fileService.upload(any())).thenThrow(new FileTooLargeException(DataSize.ofMegabytes(5)));

        mockMvc.perform(multipart("/api/files").file(filePart("photo.png", FileSamples.PNG)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("File exceeds the maximum allowed size of 5 MB"));
    }

    @Test
    void listReturnsPagedMetadata() throws Exception {
        StoredFile file = storedFile("doc.pdf", "application/pdf", 42);
        when(fileService.list(1, 5)).thenReturn(new PageImpl<>(List.of(file), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/files").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(file.getId().toString()))
                .andExpect(jsonPath("$.content[0].originalName").value("doc.pdf"))
                .andExpect(jsonPath("$.content[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.content[0].size").value(42))
                .andExpect(jsonPath("$.content[0].uploadedAt").value("2026-10-19T10:15:30Z"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listUsesDefaultPaging() throws Exception {
        when(fileService.list(0, 20)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/files")).andExpect(status().isOk());

        verify(fileService).list(0, 20);
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/files").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        verifyNoInteractions(fileService);
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/files").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        verifyNoInteractions(fileService);
    }

    @Test
    void getReturnsMetadata() throws Exception {
        StoredFile file = storedFile("doc.pdf", "application/pdf", 42);
        when(fileService.get(file.getId())).thenReturn(file);

        mockMvc.perform(get("/api/files/{id}", file.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("doc.pdf"));
    }

    @Test
    void getRejectsMalformedId() throws Exception {
        mockMvc.perform(get("/api/files/{id}", "not-a-uuid")).andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void downloadReturnsBytesWithDetectedTypeAndSafeAttachmentHeader() throws Exception {
        UUID id = UUID.randomUUID();
        String originalName = "résumé \"final\";.pdf";
        when(fileService.download(id))
                .thenReturn(new FileDownload(originalName, "application/pdf", new ByteArrayResource(FileSamples.PDF)));

        MvcResult result = mockMvc.perform(get("/api/files/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(FileSamples.PDF))
                .andReturn();

        String header = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        ContentDisposition disposition = ContentDisposition.parse(header);
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo(originalName);
        assertThat(disposition.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(header).doesNotContain("\r", "\n");
    }

    @Test
    void downloadReturns404ForUnknownFile() throws Exception {
        UUID id = UUID.randomUUID();
        when(fileService.download(id)).thenThrow(new NotFoundException("File " + id + " was not found"));

        mockMvc.perform(get("/api/files/{id}/download", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("File " + id + " was not found"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNoContent());

        verify(fileService).delete(id);
    }

    @Test
    void deleteReturns404ForUnknownFile() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotFoundException("File " + id + " was not found"))
                .when(fileService)
                .delete(id);

        mockMvc.perform(delete("/api/files/{id}", id)).andExpect(status().isNotFound());
    }

    private static MockMultipartFile filePart(String name, byte[] content) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }

    private static StoredFile storedFile(String name, String contentType, long size) {
        StoredFile file = new StoredFile(name, contentType, size, UUID.randomUUID());
        ReflectionTestUtils.setField(file, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(file, "uploadedAt", UPLOADED_AT);
        return file;
    }
}
