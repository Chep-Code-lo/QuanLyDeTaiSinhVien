package com.example.qldetai.feature.progress.repository;

import com.example.qldetai.feature.progress.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> { }
