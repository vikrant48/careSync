package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.HospitalMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalMasterRepository extends JpaRepository<HospitalMaster, Long> {
    List<HospitalMaster> findByOrgId(Long orgId);
}
