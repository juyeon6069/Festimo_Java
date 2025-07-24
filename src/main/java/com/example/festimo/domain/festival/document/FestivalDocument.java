package com.example.festimo.domain.festival.document;

import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDate;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "festivals")
public class FestivalDocument {
    @Id
    private Long festival_id = 0L;
    private String title = "";
    private String address = "";

    @Field(type = FieldType.Date, pattern = "yyyy/MM/dd")
    private LocalDate startDate;

    @Field(type = FieldType.Date, pattern = "yyyy/MM/dd")
    private LocalDate endDate;

    private String image;
}
