-- PostgreSQL 전용 스키마
-- CS 만족도 + 평가대상자

CREATE TABLE IF NOT EXISTS tb_cs_dissatisfaction_type (
    type_id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_dissat_code UNIQUE (type_code),
    CONSTRAINT uq_cs_dissat_name UNIQUE (type_name)
);

CREATE TABLE IF NOT EXISTS tb_cs_satisfaction_dept_monthly_target (
    id BIGSERIAL PRIMARY KEY,
    target_date DATE NOT NULL,
    second_depth_dept_id INTEGER NOT NULL,
    target_percent NUMERIC(6,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_dept_monthly_target UNIQUE (target_date, second_depth_dept_id)
);

CREATE INDEX IF NOT EXISTS idx_cs_dept_monthly_target_date_dept
    ON tb_cs_satisfaction_dept_monthly_target (second_depth_dept_id, target_date);

CREATE TABLE IF NOT EXISTS tb_cs_satisfaction_skill_target (
    id BIGSERIAL PRIMARY KEY,
    target_date DATE NOT NULL,
    skill_name VARCHAR(50) NOT NULL,
    target_percent NUMERIC(6,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_cs_skill_target_date_skill UNIQUE (target_date, skill_name)
);

CREATE INDEX IF NOT EXISTS idx_cs_skill_target_date
    ON tb_cs_satisfaction_skill_target (target_date);

CREATE TABLE IF NOT EXISTS tb_cs_satisfaction_annual_target (
    id BIGSERIAL PRIMARY KEY,
    target_year INTEGER NOT NULL,
    task_code VARCHAR(50) NOT NULL,
    task_name VARCHAR(100) NOT NULL,
    target_percent NUMERIC(6,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_cs_annual_target_year_code UNIQUE (target_year, task_code)
);

CREATE INDEX IF NOT EXISTS idx_cs_annual_target_year
    ON tb_cs_satisfaction_annual_target (target_year);

CREATE TABLE IF NOT EXISTS tb_you_cs (
    id BIGSERIAL PRIMARY KEY,
    "자회사구분" VARCHAR,
    "상담사id" VARCHAR NOT NULL,
    "상담일자" VARCHAR NOT NULL,
    "상담시간" VARCHAR,
    "상담유형1" VARCHAR,
    "상담유형2" VARCHAR,
    "상담유형3" VARCHAR,
    "불만족유형" SMALLINT,
    "스킬" VARCHAR,
    "긍정코멘트" VARCHAR,
    "부정코멘트" VARCHAR,
    "만족여부" VARCHAR NOT NULL,
    "5대도시" VARCHAR,
    "5060" VARCHAR,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tb_you_cs_상담일자 ON tb_you_cs ("상담일자");
CREATE INDEX IF NOT EXISTS idx_tb_you_cs_상담사id ON tb_you_cs ("상담사id");
CREATE INDEX IF NOT EXISTS idx_tb_you_cs_상담일자_상담사id ON tb_you_cs ("상담일자", "상담사id");

CREATE TABLE IF NOT EXISTS "TB_YOU_TARGET" (
    "회사" VARCHAR,
    "센터" VARCHAR,
    "상담사ID" VARCHAR,
    "문서보안ID" VARCHAR,
    "성명" VARCHAR,
    "그룹" VARCHAR,
    "실" VARCHAR,
    "스킬" VARCHAR,
    "직책" VARCHAR,
    "평가대상여부" VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_tb_you_target_상담사id ON "TB_YOU_TARGET" ("상담사ID");
