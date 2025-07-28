package com.example.festimo.festival;

import com.example.festimo.domain.festival.domain.Festival;
import com.example.festimo.domain.festival.dto.FestivalTO;
import com.example.festimo.domain.festival.repository.FestivalRepository;
import com.example.festimo.domain.festival.service.FestivalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.modelmapper.config.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
@SpringBootTest
@ActiveProfiles("test")
public class RedisCachePerformanceTest {
    @Autowired
    private FestivalService festivalService;

    @Autowired
    private FestivalRepository festivalRepository;

//    @Autowired
    private ModelMapper modelMapper;

    @BeforeEach
    void setUpTestData() throws IOException {
        modelMapper = new ModelMapper();
        TypeMap<FestivalTO, Festival> typeMap = modelMapper.createTypeMap(FestivalTO.class, Festival.class);
        typeMap.addMappings(mapper -> {
            mapper.map(FestivalTO::getXCoordinate, Festival::setXCoordinate);
            mapper.map(FestivalTO::getYCoordinate, Festival::setYCoordinate);
        });

        modelMapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(Configuration.AccessLevel.PRIVATE);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        InputStream is = getClass().getClassLoader().getResourceAsStream("mock/festivals.json");

        FestivalTO[] festivals = mapper.readValue(is, FestivalTO[].class);

        for (FestivalTO dto : festivals) {
            Festival entity = modelMapper.map(dto, Festival.class);
            festivalRepository.save(entity);
        }
    }

    @Test
    public void testRedisCacheEffectiveness() {
        Pageable pageable = PageRequest.of(0, 10);
        int iterations = 10;
        long totalMiss = 0;
        long totalHit = 0;

        // 캐시 미스: 첫 요청 한 번
        long missStart = System.nanoTime();
        festivalService.findPaginatedWithCache(pageable); // 캐시 저장
        totalMiss = System.nanoTime() - missStart;

        // 캐시 히트: 동일 요청 여러 번 반복
        for (int i = 0; i < iterations; i++) {
            long hitStart = System.nanoTime();
            festivalService.findPaginatedWithCache(pageable);
            totalHit += System.nanoTime() - hitStart;
        }

        double avgHitMs = totalHit / 1_000_000.0 / iterations;
        double missMs = totalMiss / 1_000_000.0;

        System.out.printf("Cache MISS: %.2f ms\n", missMs);
        System.out.printf("Cache HIT (avg of %d): %.2f ms\n", iterations, avgHitMs);

        assertTrue(avgHitMs < missMs, "Cache hit should be faster than miss");
    }
}
