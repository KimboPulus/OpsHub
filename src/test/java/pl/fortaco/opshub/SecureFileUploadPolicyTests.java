package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import pl.fortaco.opshub.service.SecureFileUploadPolicy;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureFileUploadPolicyTests {
    @ParameterizedTest
    @CsvSource({
        "photo.jpg,image/jpeg",
        "photo.jpeg,image/jpeg",
        "part.png,image/png",
        "weld.webp,image/webp"
    })
    void allowsExpectedImageTypes(String fileName, String contentType) {
        MockMultipartFile file = image(fileName, contentType, 1024);

        assertDoesNotThrow(() -> SecureFileUploadPolicy.validate(file));
    }

    @ParameterizedTest
    @CsvSource({
        "../secret.png,image/png",
        "folder/secret.png,image/png",
        "virus.exe,image/png",
        "photo.png,application/octet-stream"
    })
    void blocksSuspiciousUploads(String fileName, String contentType) {
        MockMultipartFile file = image(fileName, contentType, 1024);

        assertThrows(IllegalArgumentException.class, () -> SecureFileUploadPolicy.validate(file));
    }

    @Test
    void blocksOversizedImages() {
        MockMultipartFile file = image("huge.jpg", "image/jpeg", SecureFileUploadPolicy.MAX_IMAGE_BYTES + 1);

        assertThrows(IllegalArgumentException.class, () -> SecureFileUploadPolicy.validate(file));
    }

    @Test
    void generatedStoredNameKeepsSafeExtensionAndRandomizesOriginalName() {
        String first = SecureFileUploadPolicy.storedFileName("operator-photo.JPG");
        String second = SecureFileUploadPolicy.storedFileName("operator-photo.JPG");

        assertTrue(first.endsWith(".jpg"));
        assertTrue(second.endsWith(".jpg"));
        assertNotEquals(first, second);
    }

    private static MockMultipartFile image(String fileName, String contentType, long size) {
        byte[] bytes = "x".repeat((int) size).getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile("file", fileName, contentType, bytes);
    }
}
