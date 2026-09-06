package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.GenderMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface GenderMasterRepository extends JpaRepository<GenderMaster, Long> {
    List<GenderMaster> findByOrgId(Long orgId);

    List<GenderMaster> findByOrgIdAndValue(Long orgId, String value);

    @Transactional
    void deleteByOrgIdAndValue(Long orgId, String value);
}
