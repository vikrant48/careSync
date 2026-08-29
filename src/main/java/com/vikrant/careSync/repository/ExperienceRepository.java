package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.Experience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    @Query("SELECT e FROM Experience e WHERE e.doctor.id = :doctorId")
    List<Experience> findByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT DISTINCT e.doctor.id FROM Experience e WHERE e.doctor.id IN :doctorIds")
    List<Long> findDoctorIdsWithExperienceIn(@Param("doctorIds") List<Long> doctorIds);

    @Override
    Optional<Experience> findById(Long id);

    @Override
    <S extends Experience> S save(S entity);

    @Override
    void deleteById(Long id);
}