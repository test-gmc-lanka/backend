package com.gmc.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gmc.backend.exception.InvalidFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link FileStorageServiceImpl}, writing into a JUnit temp directory.
 */
class FileStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private FileStorageServiceImpl fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
    }

    @Test
    void storeImages_savesFilesAndReturnsPublicUrls() {
        MockMultipartFile file = new MockMultipartFile("files", "chair.JPG", "image/jpeg", new byte[] {1, 2, 3});

        List<String> urls = fileStorageService.storeImages(List.of(file), "maintenance");

        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).startsWith("/uploads/maintenance/").endsWith(".jpg");
        String storedName = urls.get(0).substring("/uploads/maintenance/".length());
        assertThat(Files.exists(tempDir.resolve("maintenance").resolve(storedName)))
                .isTrue();
    }

    @Test
    void storeImages_skipsEmptyFiles() {
        MockMultipartFile empty = new MockMultipartFile("files", "a.png", "image/png", new byte[0]);

        assertThat(fileStorageService.storeImages(List.of(empty), "products")).isEmpty();
    }

    @Test
    void storeImages_disallowedExtension_throws() {
        MockMultipartFile file =
                new MockMultipartFile("files", "script.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> fileStorageService.storeImages(List.of(file), "products"))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void storeImages_noFiles_throws() {
        assertThatThrownBy(() -> fileStorageService.storeImages(List.of(), "products"))
                .isInstanceOf(InvalidFileException.class);
    }
}
