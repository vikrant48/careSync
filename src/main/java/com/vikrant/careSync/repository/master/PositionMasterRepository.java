package com.vikrant.careSync.repository.master;

import com.vikrant.careSync.entity.master.PositionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PositionMasterRepository extends JpaRepository<PositionMaster, Long> {
    List<PositionMaster> findByOrgId(Long orgId);
}
