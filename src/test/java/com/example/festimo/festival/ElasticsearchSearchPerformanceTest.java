package com.example.festimo.festival;

import com.example.festimo.domain.festival.repository.FestivalRepository;
import com.example.festimo.domain.festival.service.FestivalService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SpringBootTest
@ActiveProfiles("test")
public class ElasticsearchSearchPerformanceTest {

    @Autowired
    private FestivalService festivalService;

    @Autowired
    private FestivalRepository festivalRepository;

    private final int iterations = 10;
    private final Pageable pageable = PageRequest.of(0, 20);
    private final String keyword = "꽃";

    @BeforeEach
    void init() {
        festivalService.syncFestivalData();
    }

    private long measureDbSearchPerformance() {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            festivalRepository.findByTitleContainingIgnoreCase(keyword, pageable);
            long duration = System.nanoTime() - start;
            totalTime += duration;
        }

        return totalTime / iterations;
    }

    private long measureSearchPerformance() {
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            festivalService.search(keyword, pageable);
            long duration = System.nanoTime() - start;
            totalTime += duration;
        }

        return totalTime / iterations;
    }

    @Test
    @DisplayName("엘라스틱서치 검색 속도 테스트")
    public void testSearchPerformance() {
        long avgDBTime = measureDbSearchPerformance();
        long avgTime = measureSearchPerformance();

        double avgDBMs = avgDBTime / 1_000_000.0;
        double avgMs = avgTime / 1_000_000.0;

        System.out.printf("[DB] '%s' 키워드 평균 검색 시간: %.2f ms\n", keyword, avgDBMs);
        System.out.printf("[Elasticsearch] '%s' 키워드 평균 검색 시간: %.2f ms\n", keyword, avgMs);
    }
}
