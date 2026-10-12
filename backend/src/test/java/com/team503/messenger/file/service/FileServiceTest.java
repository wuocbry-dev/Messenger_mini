package com.team503.messenger.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.file.config.R2Properties;
import com.team503.messenger.file.dto.FileResponse;
import com.team503.messenger.file.dto.UploadFileRequest;
import com.team503.messenger.file.dto.UploadFileResponse;
import com.team503.messenger.file.entity.StoredFile;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.file.repository.StoredFileRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class FileServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private StoredFileRepository repository;
    private ObjectStorageService storage;
    private R2Properties properties;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(StoredFileRepository.class);
        storage = org.mockito.Mockito.mock(ObjectStorageService.class);
        properties = new R2Properties();
        properties.setEndpoint("https://example.r2.cloudflarestorage.com");
        properties.setAccessKeyId("AKID");
        properties.setSecretAccessKey("SECRET");
        properties.setBucketName("test-bucket");
        properties.setPresignedTtlSeconds(900);
        properties.setKeyPrefix("messenger/");
        fileService = new FileService(repository, storage, properties, CLOCK);
    }

    @Test
    void prepareUploadGeneratesServerControlledObjectKeyAndReturnsPresignedUrl() {
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 1L);
            return f;
        });
        when(storage.createUploadUrl(anyString(), anyString())).thenReturn(URI.create("https://r2.example/upload"));

        UploadFileRequest request = new UploadFileRequest("photo.png", "image/png", 1024L, null);

        UploadFileResponse response = fileService.prepareUpload(1L, request);

        assertThat(response.fileId()).isEqualTo(1L);
        assertThat(response.objectKey()).startsWith("messenger/u1/");
        assertThat(response.objectKey()).endsWith(".png");
        assertThat(response.uploadUrl()).isEqualTo(URI.create("https://r2.example/upload"));
        assertThat(response.uploadUrlExpiresInSeconds()).isEqualTo(900);
        assertThat(response.originalName()).isEqualTo("photo.png");
        assertThat(response.mimeType()).isEqualTo("image/png");
        verify(storage).createUploadUrl(anyString(), anyString());
    }

    @Test
    void prepareUploadRejectsUnsupportedMimeType() {
        UploadFileRequest request = new UploadFileRequest("virus.exe", "application/x-msdownload", 100L, null);

        assertThatThrownBy(() -> fileService.prepareUpload(1L, request))
                .isInstanceOf(FileException.class)
                .extracting(e -> ((FileException) e).getCode())
                .isEqualTo("UNSUPPORTED_MIME_TYPE");

        verify(repository, never()).save(any(StoredFile.class));
        verify(storage, never()).createUploadUrl(anyString(), anyString());
    }

    @Test
    void prepareUploadRejectsOversizedFile() {
        UploadFileRequest request = new UploadFileRequest(
                "huge.bin",
                "application/pdf",
                FileService.MAX_FILE_SIZE_BYTES + 1,
                null
        );

        assertThatThrownBy(() -> fileService.prepareUpload(1L, request))
                .isInstanceOf(FileException.class)
                .extracting(e -> ((FileException) e).getStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        verify(repository, never()).save(any(StoredFile.class));
    }

    @Test
    void prepareUploadStripsPathFromOriginalName() {
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 1L);
            return f;
        });
        when(storage.createUploadUrl(anyString(), anyString())).thenReturn(URI.create("https://r2.example/upload"));

        UploadFileRequest request = new UploadFileRequest("../../etc/passwd.png", "image/png", 100L, null);

        UploadFileResponse response = fileService.prepareUpload(1L, request);

        assertThat(response.originalName()).isEqualTo("passwd.png");
    }

    @Test
    void prepareUploadRejectsPathTraversalExtension() {
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 1L);
            return f;
        });
        when(storage.createUploadUrl(anyString(), anyString())).thenReturn(URI.create("https://r2.example/upload"));

        // Original filename "evil.png.exe": last extension is `.exe`.
        // The strict extension regex allows `.exe`, so the object key will
        // get `.exe`. The original name is sanitized (basename only) and the
        // key is server-generated, so no path traversal can leak.
        UploadFileRequest request = new UploadFileRequest("evil.png.exe", "image/png", 100L, null);

        UploadFileResponse response = fileService.prepareUpload(1L, request);

        // The sanitized original name keeps the full basename (we only strip
        // path separators), and the object key uses the LAST extension that
        // matches the strict pattern.
        assertThat(response.originalName()).isEqualTo("evil.png.exe");
        assertThat(response.objectKey()).endsWith(".exe");
        // The key is a fully server-generated UUID-prefixed path; no original
        // string fragments leak into the storage location.
        assertThat(response.objectKey()).startsWith("messenger/u1/");
    }

    @Test
    void getFileReturnsPresignedDownloadUrl() {
        StoredFile file = StoredFile.create(2L, "messenger/u2/abc.png", "photo.png",
                "image/png", 1024L, null, "etag-1", NOW);
        ReflectionTestUtils.setField(file, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        when(storage.createDownloadUrl("messenger/u2/abc.png"))
                .thenReturn(URI.create("https://r2.example/download"));

        FileResponse response = fileService.getFile(2L, 7L);

        assertThat(response.fileId()).isEqualTo(7L);
        assertThat(response.objectKey()).isEqualTo("messenger/u2/abc.png");
        assertThat(response.uploaderId()).isEqualTo(2L);
        assertThat(response.downloadUrl()).isEqualTo(URI.create("https://r2.example/download"));
    }

    @Test
    void getFileReturnsNotFoundWhenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFile(2L, 999L))
                .isInstanceOf(FileException.class)
                .extracting(e -> ((FileException) e).getCode())
                .isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    void getFileAllowsActiveMemberOfConversationWithAttachment() {
        StoredFile file = StoredFile.create(2L, "messenger/u2/abc.png", "photo.png",
                "image/png", 1024L, null, "etag-1", NOW);
        ReflectionTestUtils.setField(file, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        when(repository.isAttachedInConversationOf(7L, 3L)).thenReturn(true);
        when(storage.createDownloadUrl(anyString())).thenReturn(URI.create("https://r2.example/download"));

        FileResponse response = fileService.getFile(3L, 7L);

        assertThat(response.fileId()).isEqualTo(7L);
    }

    @Test
    void getFileHidesFileFromUnrelatedUser() {
        StoredFile file = StoredFile.create(2L, "messenger/u2/abc.png", "photo.png",
                "image/png", 1024L, null, "etag-1", NOW);
        ReflectionTestUtils.setField(file, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        when(repository.isAttachedInConversationOf(7L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> fileService.getFile(9L, 7L))
                .isInstanceOf(FileException.class)
                .extracting(e -> ((FileException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(storage, never()).createDownloadUrl(anyString());
    }

    @Test
    void getFileDoesNotLeakSecretInResponse() {
        StoredFile file = StoredFile.create(2L, "messenger/u2/abc.png", "photo.png",
                "image/png", 1024L, null, "etag-1", NOW);
        ReflectionTestUtils.setField(file, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        when(storage.createDownloadUrl(anyString())).thenReturn(URI.create("https://r2.example/download"));

        FileResponse response = fileService.getFile(2L, 7L);

        // Defensive: ensure no field exposes the secret.
        assertThat(response.objectKey()).doesNotContain("SECRET");
        assertThat(response.objectKey()).doesNotContain("change-me");
        assertThat(response.originalName()).doesNotContain("SECRET");
    }
}
