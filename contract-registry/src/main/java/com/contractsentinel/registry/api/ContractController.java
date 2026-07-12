package com.contractsentinel.registry.api;

import com.contractsentinel.registry.db.ContractRepository;
import com.contractsentinel.registry.db.ViolationRepository;
import com.contractsentinel.registry.model.Contract;
import com.contractsentinel.registry.model.ContractViolation;
import com.contractsentinel.registry.redis.ContractCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractRepository contractRepository;
    private final ViolationRepository violationRepository;
    private final ContractCache contractCache;

    public ContractController(ContractRepository contractRepository,
                              ViolationRepository violationRepository,
                              ContractCache contractCache) {
        this.contractRepository = contractRepository;
        this.violationRepository = violationRepository;
        this.contractCache = contractCache;
    }

    @GetMapping("/contracts")
    public List<Contract> listContracts() {
        return contractRepository.findAll();
    }

    @GetMapping("/contracts/{id}")
    public ResponseEntity<Contract> getContract(@PathVariable UUID id) {
        return contractRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/contracts/pair")
    public ResponseEntity<Contract> getPairContract(
            @RequestParam String provider,
            @RequestParam String consumer,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false, defaultValue = "active") String status) {
        String ep = endpoint != null ? endpoint : "";
        Optional<Contract> cached = contractCache.getContract(provider, consumer, ep);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }
        Optional<Contract> contract = contractRepository
                .findTopByProviderIdAndConsumerIdAndEndpointAndStatusOrderByVersionDesc(
                        provider, consumer, ep, Contract.Status.valueOf(status));
        if (contract.isPresent()) {
            contractCache.cacheContract(contract.get());
            return ResponseEntity.ok(contract.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/contracts/import")
    public ResponseEntity<Contract> importContract(@RequestBody Map<String, Object> body) {
        try {
            String providerId = (String) body.get("providerId");
            String consumerId = (String) body.get("consumerId");
            String endpoint = (String) body.get("endpoint");
            String method = (String) body.get("method");
            String schemaJson = (String) body.get("schemaJson");
            String sourceStr = (String) body.getOrDefault("source", "manual");

            if (providerId == null || consumerId == null || endpoint == null || schemaJson == null) {
                return ResponseEntity.badRequest().build();
            }

            Contract contract = new Contract(
                    providerId, consumerId, endpoint, method,
                    1, schemaJson, Instant.now(),
                    Contract.Source.valueOf(sourceStr),
                    Contract.Status.active
            );
            contract = contractRepository.save(contract);
            contractCache.cacheContract(contract);
            return ResponseEntity.status(HttpStatus.CREATED).body(contract);
        } catch (Exception e) {
            log.error("Failed to import contract: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/contracts/{id}")
    public ResponseEntity<Void> archiveContract(@PathVariable UUID id) {
        Optional<Contract> opt = contractRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Contract contract = opt.get();
        contract.setStatus(Contract.Status.archived);
        contractRepository.save(contract);
        contractCache.evict(contract.getProviderId(), contract.getConsumerId(), contract.getEndpoint());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/violations")
    public List<ContractViolation> listViolations(
            @RequestParam(required = false) UUID contractId) {
        if (contractId != null) {
            return violationRepository.findByContractId(contractId);
        }
        return violationRepository.findAll();
    }

    @GetMapping("/violations/{id}")
    public ResponseEntity<ContractViolation> getViolation(@PathVariable UUID id) {
        return violationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/violations/{id}/resolve")
    public ResponseEntity<ContractViolation> resolveViolation(@PathVariable UUID id) {
        Optional<ContractViolation> opt = violationRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ContractViolation violation = opt.get();
        violation.setResolved(true);
        violationRepository.save(violation);
        return ResponseEntity.ok(violation);
    }

    @GetMapping("/violations/summary")
    public Map<String, Object> violationsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", violationRepository.count());
        summary.put("unresolved", violationRepository.countByResolved(false));
        summary.put("resolved", violationRepository.countByResolved(true));

        List<Object[]> byType = violationRepository.countByViolationType();
        Map<String, Long> typeCounts = new HashMap<>();
        for (Object[] row : byType) {
            typeCounts.put((String) row[0], (Long) row[1]);
        }
        summary.put("byType", typeCounts);
        return summary;
    }
}
