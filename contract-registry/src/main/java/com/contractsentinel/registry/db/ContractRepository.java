package com.contractsentinel.registry.db;

import com.contractsentinel.registry.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByProviderIdAndConsumerIdAndEndpoint(String providerId, String consumerId, String endpoint);

    @Query("SELECT c FROM Contract c WHERE c.providerId = :providerId AND c.consumerId = :consumerId " +
           "AND c.endpoint = :endpoint AND c.status = 'active' ORDER BY c.version DESC LIMIT 1")
    Optional<Contract> findActiveContract(@Param("providerId") String providerId,
                                          @Param("consumerId") String consumerId,
                                          @Param("endpoint") String endpoint);

    List<Contract> findByStatus(Contract.Status status);

    Optional<Contract> findTopByProviderIdAndConsumerIdAndEndpointAndStatusOrderByVersionDesc(
            String providerId, String consumerId, String endpoint, Contract.Status status);

    List<Contract> findByProviderIdAndConsumerId(String providerId, String consumerId);
}
