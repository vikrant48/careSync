package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.InstitutionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InstitutionMasterRepository extends JpaRepository<InstitutionMaster, Long> {
    List<InstitutionMaster> findByOrgId(Long orgId);

    @Transactional
    void deleteByOrgIdAndValue(Long orgId, String value);
}
