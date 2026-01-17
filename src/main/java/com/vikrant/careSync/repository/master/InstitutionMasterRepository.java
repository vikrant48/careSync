package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.InstitutionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstitutionMasterRepository extends JpaRepository<InstitutionMaster, Long> {
    List<InstitutionMaster> findByOrgId(Long orgId);
}
