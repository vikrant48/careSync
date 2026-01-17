package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.BloodGroupMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BloodGroupMasterRepository extends JpaRepository<BloodGroupMaster, Long> {
    List<BloodGroupMaster> findByOrgId(Long orgId);
}
