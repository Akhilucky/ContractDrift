package com.contractsentinel.registry.db;

import com.contractsentinel.registry.model.ContractViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ViolationRepository extends JpaRepository<ContractViolation, UUID> {

    List<ContractViolation> findByContractId(UUID contractId);

    List<ContractViolation> findByResolved(boolean resolved);

    long countByResolved(boolean resolved);

    @Query("SELECT v.violationType, COUNT(v) FROM ContractViolation v GROUP BY v.violationType")
    List<Object[]> countByViolationType();

    long countByViolationTypeAndResolved(String violationType, boolean resolved);
}
