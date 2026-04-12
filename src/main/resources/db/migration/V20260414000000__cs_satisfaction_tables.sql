-- CS 만족도 (PostgreSQL 전용) — Hibernate PhysicalNaming 과 동일한 소문자 테이블명
-- 레거시: 따옴표 대문자 "TB_CS_*" 가 있으면 제거 후 재생성

DROP TABLE IF EXISTS tb_cs_satisfaction_record CASCADE;
DROP TABLE IF EXISTS "TB_CS_SATISFACTION_RECORD" CASCADE;

DROP TABLE IF EXISTS tb_cs_satisfaction_daily_target CASCADE;
DROP TABLE IF EXISTS "TB_CS_SATISFACTION_DAILY_TARGET" CASCADE;

DROP TABLE IF EXISTS tb_cs_dissatisfaction_type CASCADE;
DROP TABLE IF EXISTS "TB_CS_DISSATISFACTION_TYPE" CASCADE;

CREATE TABLE tb_cs_dissatisfaction_type (
    type_id       BIGSERIAL PRIMARY KEY,
    type_code     VARCHAR(64)  NOT NULL,
    type_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_dissat_code UNIQUE (type_code),
    CONSTRAINT uq_cs_dissat_name UNIQUE (type_name)
);

CREATE TABLE tb_cs_satisfaction_daily_target (
    id                    BIGSERIAL PRIMARY KEY,
    target_date           DATE         NOT NULL,
    second_depth_dept_id  INTEGER      NOT NULL,
    target_percent        NUMERIC(6,2) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_sat_daily_target UNIQUE (target_date, second_depth_dept_id)
);

CREATE INDEX idx_cs_sat_target_date_dept
    ON tb_cs_satisfaction_daily_target (second_depth_dept_id, target_date);

CREATE TABLE tb_cs_satisfaction_record (
    id                       BIGSERIAL PRIMARY KEY,
    eval_date                DATE         NOT NULL,
    skid                     VARCHAR(64)  NOT NULL,
    satisfied_yn             CHAR(1)      NOT NULL,
    score                    NUMERIC(10,2),
    dissatisfaction_type_id  BIGINT,
    five_major_cities_yn     CHAR(1),
    gen_5060_yn              CHAR(1),
    problem_resolved_yn      CHAR(1),
    good_ment                TEXT,
    bad_ment                 TEXT,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cs_sat_dissat FOREIGN KEY (dissatisfaction_type_id)
        REFERENCES tb_cs_dissatisfaction_type (type_id) ON DELETE SET NULL
);

CREATE INDEX idx_cs_sat_record_eval_date ON tb_cs_satisfaction_record (eval_date);
CREATE INDEX idx_cs_sat_record_skid ON tb_cs_satisfaction_record (skid);
CREATE INDEX idx_cs_sat_record_date_skid ON tb_cs_satisfaction_record (eval_date, skid);
