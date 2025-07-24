package com.example.festimo.domain.festival.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@NoArgsConstructor
@ToString
@Setter
@Getter
@Entity
@Table(
        name = "festival",
        indexes = {
                @Index(name = "idx_festival_date", columnList = "start_date, end_date"),
                @Index(name = "idx_festival_address", columnList = "address")
        }
)
public class Festival {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int festival_id;
    private String title;
    private String category;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String address;
    private String image;
    private Float xCoordinate;
    private Float yCoordinate;
    private String phone;
    private int contentId;
}
