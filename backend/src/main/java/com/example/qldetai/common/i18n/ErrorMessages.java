package com.example.qldetai.common.i18n;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Keeps internal exception text separate from the Vietnamese API error contract. */
public final class ErrorMessages {
    private static final Properties MESSAGES = load();
    private static final String FALLBACK = "Không thể thực hiện thao tác. Vui lòng thử lại.";

    private ErrorMessages() {}

    public static String vietnamese(String message) {
        if (message == null || message.isBlank()) return FALLBACK;
        String key = message.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        String translated = MESSAGES.getProperty(key);
        if (translated != null) return translated;
        if (message.startsWith("Project assignment cannot change from ")) {
            return "Không thể chuyển trạng thái đề tài theo yêu cầu. Vui lòng kiểm tra trạng thái hiện tại.";
        }
        if (message.startsWith("Registration period cannot change from ")) {
            return "Không thể chuyển trạng thái đợt đăng ký theo yêu cầu. Vui lòng kiểm tra trạng thái hiện tại.";
        }
        // Existing messages authored in Vietnamese already carry their user-facing wording.
        if (message.matches("(?s).*[À-ỹĐđ].*")) return message;
        return FALLBACK;
    }

    private static Properties load() {
        Properties result = new Properties();
        try (var stream = ErrorMessages.class.getResourceAsStream("/api-errors-vi.properties")) {
            if (stream == null) throw new IllegalStateException("Missing Vietnamese error catalog");
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return result;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load Vietnamese error catalog", exception);
        }
    }
}
