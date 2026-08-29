package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Education;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EducationRepository extends JpaRepository<Education, Long> {

    @Query("SELECT e FROM Education e WHERE e.doctor.id = :doctorId")
    List<Education> findByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT DISTINCT e.doctor.id FROM Education e WHERE e.doctor.id IN :doctorIds")
    List<Long> findDoctorIdsWithEducationIn(@Param("doctorIds") List<Long> doctorIds);

    @Override
    Optional<Education> findById(Long id);

    @Override
    <S extends Education> S save(S entity);

    @Override
    void deleteById(Long id);
}