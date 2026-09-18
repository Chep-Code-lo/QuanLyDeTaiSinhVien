package com.example.qldetai.feature.identity;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.feature.identity.IdentityDtos.LecturerDirectoryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LecturerDirectoryService {

    private final LecturerRepository lecturerRepository;
    private final IdentityMapper identityMapper;

    public LecturerDirectoryService(
            LecturerRepository lecturerRepository,
            IdentityMapper identityMapper) {
        this.lecturerRepository = lecturerRepository;
        this.identityMapper = identityMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<LecturerDirectoryResponse> list(
            String search,
            Long facultyId,
            Long departmentId,
            int page,
            int size) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.from(lecturerRepository.search(
                        normalizedSearch,
                        facultyId,
                        departmentId,
                        UserStatus.ACTIVE,
                        PageRequest.of(page, size, Sort.by("lecturerCode").ascending()))
                .map(identityMapper::toLecturerDirectory));
    }
}
