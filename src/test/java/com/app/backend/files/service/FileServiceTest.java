package com.app.backend.files.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.files.FileSamples;
import com.app.backend.files.config.FilesProperties;
import com.app.backend.files.domain.FileTooLargeException;
import com.app.backend.files.domain.StoredFile;
import com.app.backend.files.domain.UnsupportedFileTypeException;
import com.app.backend.files.persistence.FileStorage;
import com.app.backend.files.persistence.StoredFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final DataSize MAX_SIZE = DataSize.ofMegabytes(5);

    @Mock
    private StoredFileRepository repository;

    @Mock
    private FileStorage storage;

    @TempDir
    private Path tempDir;

    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(repository, storage, new FilesProperties(tempDir, MAX_SIZE));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void storesValidPngWithDetectedTypeAndSanitizedName() {
        UUID key = UUID.randomUUID();
        when(storage.store(any())).thenReturn(key);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = multipart("../../evil.png", "application/octet-stream", FileSamples.PNG);

        StoredFile saved = service.upload(file);

        assertThat(saved.getOriginalName()).isEqualTo("evil.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getSize()).isEqualTo(FileSamples.PNG.length);
        assertThat(saved.getStorageKey()).isEqualTo(key);
    }

    @Test
    void writesContentBeforeSavingRecord() {
        when(storage.store(any())).thenReturn(UUID.randomUUID());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(multipart("doc.pdf", "application/pdf", FileSamples.PDF));

        InOrder order = inOrder(storage, repository);
        order.verify(storage).store(any());
        order.verify(repository).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.JPEG", "photo"})
    void acceptsJpegWithMatchingOrMissingExtension(String name) {
        when(storage.store(any())).thenReturn(UUID.randomUUID());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoredFile saved = service.upload(multipart(name, "text/plain", FileSamples.JPEG));

        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getOriginalName()).isEqualTo(name);
    }

    @Test
    void acceptsFileExactlyAtSizeLimit() {
        when(storage.store(any())).thenReturn(UUID.randomUUID());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] content = FileSamples.pngOfSize((int) MAX_SIZE.toBytes());

        StoredFile saved = service.upload(multipart("big.png", "image/png", content));

        assertThat(saved.getSize()).isEqualTo(MAX_SIZE.toBytes());
    }

    @Test
    void rejectsFileLargerThanSizeLimit() {
        byte[] content = FileSamples.pngOfSize((int) MAX_SIZE.toBytes() + 1);

        assertThatThrownBy(() -> service.upload(multipart("big.png", "image/png", content)))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessage("File exceeds the maximum allowed size of 5 MB")
                .extracting("status")
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> service.upload(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must not be empty");
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.upload(multipart("empty.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must not be empty");
        verifyNoInteractions(storage, repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../", ".."})
    void rejectsFileWithoutUsableName(String name) {
        assertThatThrownBy(() -> service.upload(multipart(name, "image/png", FileSamples.PNG)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("must have a file name");
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsExecutableRenamedToPngEvenWithPngContentType() {
        assertThatThrownBy(() -> service.upload(multipart("photo.png", "image/png", FileSamples.EXE)))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("Unsupported file type: only JPEG, PNG and PDF files are allowed")
                .extracting("status")
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsPlainTextFile() {
        assertThatThrownBy(() -> service.upload(multipart("notes.txt", "text/plain", FileSamples.TEXT)))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsPdfNamedAsPng() {
        assertThatThrownBy(() -> service.upload(multipart("photo.png", "image/png", FileSamples.PDF)))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("File extension does not match the file content, which was detected as application/pdf");
        verifyNoInteractions(storage, repository);
    }

    @Test
    void rejectsPngWithExecutableExtension() {
        assertThatThrownBy(() -> service.upload(multipart("photo.exe", "image/png", FileSamples.PNG)))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void deletesStoredContentWhenTransactionDoesNotCommit() {
        UUID key = UUID.randomUUID();
        when(storage.store(any())).thenReturn(key);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.upload(multipart("photo.png", "image/png", FileSamples.PNG));

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).deleteQuietly(key);
    }

    @Test
    void keepsStoredContentWhenTransactionCommits() {
        UUID key = UUID.randomUUID();
        when(storage.store(any())).thenReturn(key);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.upload(multipart("photo.png", "image/png", FileSamples.PNG));

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).deleteQuietly(any());
    }

    @Test
    void registersCleanupBeforeSavingSoFailedSaveRemovesContent() {
        UUID key = UUID.randomUUID();
        when(storage.store(any())).thenReturn(key);
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.upload(multipart("photo.png", "image/png", FileSamples.PNG)))
                .isInstanceOf(IllegalStateException.class);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).deleteQuietly(key);
    }

    @Test
    void listsNewestFirst() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.list(2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id")));
    }

    @Test
    void returnsRepositoryPage() {
        StoredFile file = new StoredFile("a.png", "image/png", 10, UUID.randomUUID());
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(file)));

        Page<StoredFile> page = service.list(0, 20);

        assertThat(page.getContent()).containsExactly(file);
    }

    @Test
    void getRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void downloadsStoredContentWithMetadata() throws IOException {
        UUID id = UUID.randomUUID();
        StoredFile file = new StoredFile("report.pdf", "application/pdf", FileSamples.PDF.length, UUID.randomUUID());
        Path content = Files.write(tempDir.resolve("content"), FileSamples.PDF);
        when(repository.findById(id)).thenReturn(Optional.of(file));
        when(storage.find(file.getStorageKey())).thenReturn(Optional.of(content));

        FileDownload download = service.download(id);

        assertThat(download.originalName()).isEqualTo("report.pdf");
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.content().getContentAsByteArray()).isEqualTo(FileSamples.PDF);
    }

    @Test
    void downloadRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(id)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void downloadRejectsRecordWhoseContentIsMissing() {
        UUID id = UUID.randomUUID();
        StoredFile file = new StoredFile("report.pdf", "application/pdf", 10, UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.of(file));
        when(storage.find(file.getStorageKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Content of file " + id + " was not found");
    }

    @Test
    void deletesRecordAndStoredContent() {
        UUID id = UUID.randomUUID();
        StoredFile file = new StoredFile("report.pdf", "application/pdf", 10, UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.of(file));

        service.delete(id);

        InOrder order = inOrder(repository, storage);
        order.verify(repository).delete(file);
        order.verify(repository).flush();
        order.verify(storage).delete(file.getStorageKey());
    }

    @Test
    void deleteRejectsUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
        verifyNoInteractions(storage);
    }

    private static MockMultipartFile multipart(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
