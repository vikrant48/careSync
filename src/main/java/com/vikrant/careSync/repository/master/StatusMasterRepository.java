package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.StatusMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StatusMasterRepository extends JpaRepository<StatusMaster, Long> {
    List<StatusMaster> findByOrgId(Long orgId);
}
