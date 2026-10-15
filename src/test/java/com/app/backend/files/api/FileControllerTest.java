package com.app.backend.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.FileType;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.service.FileDownload;
import com.app.backend.files.service.FileService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-10-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void uploadReturns201WithLocationAndMetadata() throws Exception {
        when(fileService.upload(any())).thenReturn(storedFile(12L, "photo.png", FileType.PNG, 2048));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/files/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.originalName").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(2048))
                .andExpect(jsonPath("$.uploadedAt").value("2026-10-15T10:00:00Z"));
    }

    @Test
    void uploadMapsUnsupportedTypeTo415Problem() throws Exception {
        when(fileService.upload(any()))
                .thenThrow(new UnsupportedFileTypeException("Only JPEG, PNG and PDF files are allowed"));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Only JPEG, PNG and PDF files are allowed"));
    }

    @Test
    void uploadMapsTooLargeTo413Problem() throws Exception {
        when(fileService.upload(any()))
                .thenThrow(new FileTooLargeException("File exceeds the maximum size of 5242880 bytes"));
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[] {1});

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("File exceeds the maximum size of 5242880 bytes"));
    }

    @Test
    void listReturnsPageNewestFirst() throws Exception {
        Pageable expected = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id")));
        when(fileService.list(expected))
                .thenReturn(new PageImpl<>(List.of(storedFile(4L, "a.pdf", FileType.PDF, 10)), expected, 3));

        mockMvc.perform(get("/api/files").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(4))
                .andExpect(jsonPath("$.content[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/files").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void downloadSetsContentTypeLengthAndEncodedAttachmentName() throws Exception {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47};
        when(fileService.download(5L))
                .thenReturn(new FileDownload(
                        "résumé \"final\".png", FileType.PNG, bytes.length, new ByteArrayResource(bytes)));

        mockMvc.perform(get("/api/files/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"=?UTF-8?Q?r=C3=A9sum=C3=A9_=22final=22.png?=\"; "
                                        + "filename*=UTF-8''r%C3%A9sum%C3%A9%20%22final%22.png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void downloadOfUnknownIdReturns404() throws Exception {
        when(fileService.download(9L)).thenThrow(new NotFoundException("File 9 not found"));

        mockMvc.perform(get("/api/files/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("File 9 not found"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/files/3")).andExpect(status().isNoContent());

        verify(fileService).delete(3L);
    }

    @Test
    void deleteOfUnknownIdReturns404() throws Exception {
        doThrow(new NotFoundException("File 8 not found")).when(fileService).delete(8L);

        mockMvc.perform(delete("/api/files/8")).andExpect(status().isNotFound());
    }

    private static StoredFile storedFile(Long id, String name, FileType type, long size) {
        StoredFile file = new StoredFile("generated." + type.storageExtension(), name, type, size);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "uploadedAt", UPLOADED_AT);
        return file;
    }
}
