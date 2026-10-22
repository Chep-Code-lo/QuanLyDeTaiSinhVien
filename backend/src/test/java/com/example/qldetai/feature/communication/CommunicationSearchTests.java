package com.example.qldetai.feature.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.communication.repository.CommunicationSearch;

import org.junit.jupiter.api.Test;

class CommunicationSearchTests {
    @Test
    void escapesSqlWildcardsAndNormalizesCaseWithoutDiscardingAccents() {
        assertThat(CommunicationSearch.pattern("  ĐỀ TÀI 100%_A  "))
                .isEqualTo("%đề tài 100\\%\\_a%");
        assertThat(CommunicationSearch.pattern(null)).isEqualTo("%%");
    }
}
