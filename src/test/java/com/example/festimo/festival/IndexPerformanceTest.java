package com.example.festimo.festival;

import com.example.festimo.domain.festival.service.FestivalService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IndexPerformanceTest {

    @Autowired
    private FestivalService festivalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final int iterations = 10;
    private final int year = 2024;
    private final int month = 7;
    private final Pageable pageable = PageRequest.of(1, 20);

    private void dropIndexes() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_festival_date ON festival;");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_festival_address ON festival;");
    }

    private void createIndexes() {
        jdbcTemplate.execute("CREATE INDEX idx_festival_date ON festival (start_date, end_date);");
        jdbcTemplate.execute("CREATE INDEX idx_festival_address ON festival (address);");
    }

    private long measureFilterByMonth() {
        YearMonth ym = YearMonth.of(year, month);

        long totalTime = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            festivalService.filterByMonth(year, month, pageable);
            long duration = System.nanoTime() - start;
            totalTime += duration;
        }
        return totalTime / iterations;
    }

    private long measureFilterByRegion() {
        String region = "서울";
        long totalTime = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            festivalService.filterByRegion(region, pageable);
            long duration = System.nanoTime() - start;
            totalTime += duration;
        }
        return totalTime / iterations;
    }

    @Test
    @Order(1)
    @DisplayName("인덱스 제거 후 필터링 성능 측정")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void testPerformanceWithoutIndex() {
        dropIndexes();
        logIndexExists("idx_festival_date");
        logIndexExists("idx_festival_address");

        long avgMonth = measureFilterByMonth();
        long avgRegion = measureFilterByRegion();

        System.out.printf("[인덱스 없음] 월별 필터 평균 시간: %.2f ms\n", avgMonth / 1_000_000.0);
        System.out.printf("[인덱스 없음] 지역별 필터 평균 시간: %.2f ms\n", avgRegion / 1_000_000.0);
    }

    @Test
    @Order(2)
    @DisplayName("인덱스 생성 후 필터링 성능 측정")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void testPerformanceWithIndex() {
        createIndexes();
        logIndexExists("idx_festival_date");
        logIndexExists("idx_festival_address");

        long avgMonth = measureFilterByMonth();
        long avgRegion = measureFilterByRegion();

        System.out.printf("[인덱스 있음] 월별 필터 평균 시간: %.2f ms\n", avgMonth / 1_000_000.0);
        System.out.printf("[인덱스 있음] 지역별 필터 평균 시간: %.2f ms\n", avgRegion / 1_000_000.0);
    }

    private void logIndexExists(String indexName) {
        String query = "SELECT COUNT(*) FROM information_schema.statistics WHERE table_name = 'festival' AND index_name = ?";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class, indexName);
        System.out.println("Index " + indexName + " exists? " + (count != null && count > 0));
    }
}
