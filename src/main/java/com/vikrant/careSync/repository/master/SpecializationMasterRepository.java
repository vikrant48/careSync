package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.SpecializationMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SpecializationMasterRepository extends JpaRepository<SpecializationMaster, Long> {
    List<SpecializationMaster> findByOrgId(Long orgId);

    @Transactional
    void deleteByOrgIdAndValue(Long orgId, String value);
}
