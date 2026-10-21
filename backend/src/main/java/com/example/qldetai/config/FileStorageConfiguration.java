package com.example.qldetai.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public Path fileStorageRoot(
            @Value("${app.files.storage-root:${java.io.tmpdir}/qldetai-files}") String storageRoot) {
        return Path.of(storageRoot).toAbsolutePath().normalize();
    }
}
