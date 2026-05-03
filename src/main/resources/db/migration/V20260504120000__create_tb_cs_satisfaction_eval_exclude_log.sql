-- 평가 제외(스킬·상담일시 구간) 적용 이력 — TB_YOU_CS 벌크 useYn=N 시 기록

CREATE TABLE IF NOT EXISTS tb_cs_satisfaction_eval_exclude_log (
    id BIGSERIAL PRIMARY KEY,
    skill_name VARCHAR(50) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    excluded_by_skid VARCHAR(64),
    updated_row_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cs_eval_exclude_log_created
    ON tb_cs_satisfaction_eval_exclude_log (created_at DESC);
