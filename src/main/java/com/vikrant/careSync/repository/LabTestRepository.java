package com.vikrant.careSync.repository;

import com.vikrant.careSync.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    
    List<LabTest> findByIsActiveTrue();
    
    Optional<LabTest> findByTestNameIgnoreCase(String testName);
    
    List<LabTest> findByTestNameContainingIgnoreCaseAndIsActiveTrue(String testName);
    
    @Query("SELECT lt FROM LabTest lt WHERE lt.isActive = true ORDER BY lt.testName ASC")
    List<LabTest> findAllActiveTestsOrderByName();
    
    @Query("SELECT SUM(lt.price) FROM LabTest lt WHERE lt.id IN :testIds AND lt.isActive = true")
    java.math.BigDecimal calculateTotalPriceByIds(List<Long> testIds);
}