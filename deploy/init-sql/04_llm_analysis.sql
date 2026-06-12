-- Epic 01-2-2: LLM analysis result attached to work orders.

ALTER TABLE work_order
    ADD COLUMN IF NOT EXISTS llm_analysis JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS llm_analysis_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS llm_model VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_work_order_llm_analysis
    ON work_order USING GIN (llm_analysis);
