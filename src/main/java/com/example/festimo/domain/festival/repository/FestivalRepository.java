package com.example.festimo.domain.festival.repository;

import com.example.festimo.domain.festival.domain.Festival;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FestivalRepository extends JpaRepository<Festival, String> {
    Page<Festival> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    @Query(value = "SELECT * FROM festival WHERE start_date <= :endDate AND end_date >= :startDate", nativeQuery = true)
    Page<Festival> findByMonth(@Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               Pageable pageable);

    @Query(value = "SELECT * FROM festival WHERE address LIKE CONCAT(:region, '%')", nativeQuery = true)
    Page<Festival> findByRegion(@Param("region") String region, Pageable pageable);
}
