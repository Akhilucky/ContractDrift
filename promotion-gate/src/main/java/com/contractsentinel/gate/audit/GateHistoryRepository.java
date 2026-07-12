package com.contractsentinel.gate.audit;

import com.contractsentinel.gate.model.GateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GateHistoryRepository extends JpaRepository<GateHistory, Long> {
    List<GateHistory> findByServiceIdOrderByTimestampDesc(String serviceId);
}
