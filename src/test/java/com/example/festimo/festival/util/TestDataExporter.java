package com.example.festimo.festival.util;

import com.example.festimo.domain.festival.dto.FestivalTO;
import com.example.festimo.domain.festival.service.FestivalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class TestDataExporter {
    @Autowired
    private FestivalService festivalService;

    private static final String FILE_PATH = "src/test/resources/mock/festivals.json";

    @Test
    public void exportFestivalDataToFile() throws IOException {
        File file = new File(FILE_PATH);

        if (file.exists()) {
            System.out.println("이미 존재하는 테스트 데이터파일이 실행됩니다.");
            return;
        }

        try {
            List<FestivalTO> events = festivalService.getAllEvents();
            System.out.println("총 이벤트 수: " + events.size());

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            mapper.writeValue(file, events);
            System.out.println("테스트 데이터 파일을 저장했습니다.");

        } catch (Exception e) {
            System.err.println("데이터 파일 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
