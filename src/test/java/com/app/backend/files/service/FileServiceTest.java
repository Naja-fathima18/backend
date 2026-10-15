package com.app.backend.files.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.ApiException;
import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.FileSamples;
import com.app.backend.files.domain.FileStorageException;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.FileType;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.persistence.FileStorage;
import com.app.backend.files.persistence.StoredFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @TempDir
    Path storageDir;

    @Mock
    StoredFileRepository repository;

    FileService service;

    @BeforeEach
    void setUp() {
        service = serviceStoringIn(storageDir);
    }

    @Test
    void storesJpegUnderGeneratedNameInsideStorageDirectory() throws IOException {
        returnSavedEntity();
        byte[] content = FileSamples.jpeg(1024);

        StoredFile stored = service.upload(multipart("holiday.jpg", "text/plain", content));

        assertThat(stored.getFileType()).isEqualTo(FileType.JPEG);
        assertThat(stored.getOriginalName()).isEqualTo("holiday.jpg");
        assertThat(stored.getSizeBytes()).isEqualTo(1024);
        assertThat(stored.getStorageName()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(Files.readAllBytes(storageDir.resolve(stored.getStorageName())))
                .isEqualTo(content);
    }

    @Test
    void storesPngAndPdf() {
        returnSavedEntity();

        StoredFile png = service.upload(multipart("a.png", "image/png", FileSamples.png(100)));
        StoredFile pdf = service.upload(multipart("b.pdf", "application/pdf", FileSamples.pdf(100)));

        assertThat(png.getFileType()).isEqualTo(FileType.PNG);
        assertThat(png.getStorageName()).endsWith(".png");
        assertThat(pdf.getFileType()).isEqualTo(FileType.PDF);
        assertThat(pdf.getStorageName()).endsWith(".pdf");
    }

    @Test
    void acceptsFileOfExactlyFiveMegabytes() {
        returnSavedEntity();

        StoredFile stored =
                service.upload(multipart("big.pdf", "application/pdf", FileSamples.pdf(FileSamples.FIVE_MB)));

        assertThat(stored.getSizeBytes()).isEqualTo(FileSamples.FIVE_MB);
    }

    @Test
    void rejectsFileOneByteOverFiveMegabytesWith413() throws IOException {
        MockMultipartFile tooBig = multipart("big.pdf", "application/pdf", FileSamples.pdf(FileSamples.FIVE_MB + 1));

        assertThatThrownBy(() -> service.upload(tooBig))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessage("File exceeds the maximum size of 5242880 bytes")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertNothingStored();
    }

    @Test
    void rejectsEmptyFileWith400() throws IOException {
        MockMultipartFile empty = multipart("empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(empty))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must not be empty");
        assertNothingStored();
    }

    @Test
    void rejectsMissingFileWith400() {
        assertThatThrownBy(() -> service.upload(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsFileWithoutUsableNameWith400() throws IOException {
        MockMultipartFile nameless = multipart("../", "image/png", FileSamples.png(10));

        assertThatThrownBy(() -> service.upload(nameless))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must have a file name");
        assertNothingStored();
    }

    @Test
    void rejectsExecutableRenamedToPngWith415() throws IOException {
        MockMultipartFile disguised = multipart("photo.png", "image/png", FileSamples.windowsExecutable());

        assertThatThrownBy(() -> service.upload(disguised))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("Only JPEG, PNG and PDF files are allowed")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertNothingStored();
    }

    @Test
    void rejectsPlainTextEvenWithAllowedContentType() throws IOException {
        MockMultipartFile text = multipart("notes.pdf", "application/pdf", FileSamples.text());

        assertThatThrownBy(() -> service.upload(text)).isInstanceOf(UnsupportedFileTypeException.class);
        assertNothingStored();
    }

    @Test
    void rejectsPdfContentNamedAsPng() throws IOException {
        MockMultipartFile mismatched = multipart("photo.png", "image/png", FileSamples.pdf(100));

        assertThatThrownBy(() -> service.upload(mismatched))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("File extension does not match its content, which is PDF");
        assertNothingStored();
    }

    @Test
    void ignoresTraversalInOriginalNameWhenStoring() throws IOException {
        returnSavedEntity();

        StoredFile unix = service.upload(multipart("../../etc/passwd.png", "image/png", FileSamples.png(50)));
        StoredFile windows = service.upload(multipart("..\\evil.pdf", "application/pdf", FileSamples.pdf(50)));

        assertThat(unix.getOriginalName()).isEqualTo("passwd.png");
        assertThat(windows.getOriginalName()).isEqualTo("evil.pdf");
        try (Stream<Path> files = Files.list(storageDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(unix.getStorageName(), windows.getStorageName());
        }
        try (Stream<Path> parentFiles = Files.list(storageDir.getParent())) {
            assertThat(parentFiles.map(path -> path.getFileName().toString())).doesNotContain("passwd.png", "evil.pdf");
        }
    }

    @Test
    void deletesWrittenFileWhenDatabaseInsertFails() throws IOException {
        when(repository.saveAndFlush(any(StoredFile.class))).thenThrow(new DataIntegrityViolationException("dup"));
        MockMultipartFile file = multipart("a.png", "image/png", FileSamples.png(100));

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void createsNoRecordWhenWritingFails() throws IOException {
        Path notADirectory = Files.writeString(storageDir.resolve("occupied"), "x");
        FileService brokenStorageService = serviceStoringIn(notADirectory);
        MockMultipartFile file = multipart("a.png", "image/png", FileSamples.png(100));

        assertThatThrownBy(() -> brokenStorageService.upload(file))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("Could not store the file");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void downloadReturnsStoredBytesAndMetadata() throws IOException {
        returnSavedEntity();
        byte[] content = FileSamples.png(300);
        StoredFile stored = service.upload(multipart("a.png", "image/png", content));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        FileDownload download = service.download(7L);

        assertThat(download.originalName()).isEqualTo("a.png");
        assertThat(download.fileType()).isEqualTo(FileType.PNG);
        assertThat(download.size()).isEqualTo(300);
        assertThat(download.content().getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void downloadOfUnknownIdThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("File 99 not found");
    }

    @Test
    void downloadThrowsNotFoundWhenContentIsMissingOnDisk() {
        when(repository.findById(5L)).thenReturn(Optional.of(new StoredFile("missing.png", "a.png", FileType.PNG, 10)));

        assertThatThrownBy(() -> service.download(5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesRecordAndFile() throws IOException {
        returnSavedEntity();
        StoredFile stored = service.upload(multipart("a.pdf", "application/pdf", FileSamples.pdf(100)));
        when(repository.findById(3L)).thenReturn(Optional.of(stored));

        service.delete(3L);

        verify(repository).delete(stored);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void deleteOfUnknownIdThrowsNotFoundAndKeepsFiles() throws IOException {
        returnSavedEntity();
        service.upload(multipart("a.pdf", "application/pdf", FileSamples.pdf(100)));
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(NotFoundException.class);

        verify(repository, never()).delete(any());
        assertThat(storedFiles()).hasSize(1);
    }

    private FileService serviceStoringIn(Path directory) {
        FilesProperties properties = new FilesProperties(directory, DataSize.ofMegabytes(5));
        return new FileService(
                repository, new FileStorage(directory), TransactionOperations.withoutTransaction(), properties);
    }

    private void returnSavedEntity() {
        when(repository.saveAndFlush(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertNothingStored() throws IOException {
        assertThat(storedFiles()).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    private List<Path> storedFiles() throws IOException {
        try (Stream<Path> files = Files.list(storageDir)) {
            return files.toList();
        }
    }

    private static MockMultipartFile multipart(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
