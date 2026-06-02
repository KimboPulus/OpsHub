package pl.fortaco.opshub.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SecureFileUploadPolicy {
    public static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, Set<String>> EXTENSIONS_BY_TYPE = Map.of(
        "image/jpeg", Set.of(".jpg", ".jpeg"),
        "image/png", Set.of(".png"),
        "image/webp", Set.of(".webp")
    );

    private SecureFileUploadPolicy() {
    }

    public static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Plik jest pusty.");
        }

        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Nazwa pliku wygląda podejrzanie.");
        }

        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Plik jest większy niż 8 MB.");
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Dozwolone są tylko obrazy JPG, PNG i WebP.");
        }

        boolean extensionMatches = EXTENSIONS_BY_TYPE.get(file.getContentType())
            .stream()
            .anyMatch(name::endsWith);

        if (!extensionMatches) {
            throw new IllegalArgumentException("Rozszerzenie pliku nie pasuje do typu obrazu.");
        }
    }

    public static String storedFileName(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName.toLowerCase(Locale.ROOT);
        String extension = ".jpg";

        if (name.endsWith(".png")) {
            extension = ".png";
        } else if (name.endsWith(".webp")) {
            extension = ".webp";
        } else if (name.endsWith(".jpeg")) {
            extension = ".jpeg";
        }

        return UUID.randomUUID() + extension;
    }
}
