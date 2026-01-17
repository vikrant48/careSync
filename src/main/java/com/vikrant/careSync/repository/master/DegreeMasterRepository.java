package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.DegreeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DegreeMasterRepository extends JpaRepository<DegreeMaster, Long> {
    List<DegreeMaster> findByOrgId(Long orgId);
}
