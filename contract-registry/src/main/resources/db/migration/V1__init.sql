CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(255) NOT NULL,
    consumer_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    method VARCHAR(10) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    schema_json JSONB,
    inferred_at TIMESTAMP WITH TIME ZONE,
    source VARCHAR(20) NOT NULL CHECK (source IN ('inferred', 'manual', 'imported')),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'superseded', 'archived')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE contract_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    violation_type VARCHAR(20) NOT NULL CHECK (violation_type IN ('BREAKING', 'WARNING', 'ADDITIVE')),
    diff_json JSONB,
    deployment_id VARCHAR(255),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_contracts_provider_consumer ON contracts(provider_id, consumer_id);
CREATE INDEX idx_contracts_status ON contracts(status);
CREATE INDEX idx_violations_contract_id ON contract_violations(contract_id);
CREATE INDEX idx_violations_resolved ON contract_violations(resolved);
