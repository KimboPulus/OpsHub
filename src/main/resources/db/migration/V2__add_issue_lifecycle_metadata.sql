ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS response_due_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS resolution_due_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE production_issue ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE production_issue
SET created_by = COALESCE(created_by, 'System'),
    updated_at = COALESCE(updated_at, created_at),
    response_due_at = COALESCE(response_due_at, created_at + INTERVAL '4 hours'),
    resolution_due_at = COALESCE(resolution_due_at, created_at + INTERVAL '24 hours');

CREATE INDEX IF NOT EXISTS idx_production_issue_resolution_due_at ON production_issue (resolution_due_at);
CREATE INDEX IF NOT EXISTS idx_production_issue_escalated_at ON production_issue (escalated_at);
