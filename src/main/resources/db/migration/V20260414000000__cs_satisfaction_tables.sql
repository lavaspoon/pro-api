-- =============================================================================
-- CS 만족도 관련 스키마 (PostgreSQL)
-- 기존 객체 전부 DROP 후 최종 형태로 재생성. (데이터 없음 전제)
--
-- 포함: 불만족 유형 마스터, 부서 월간 목표, 스킬 월간 목표, 연간 과제 목표,
--       만족도 레코드(엑셀 양식)
--
-- 이전에 V20260416000000 / V20260417000000 만 적용된 이력이 있으면
-- flyway_schema_history 에서 해당 버전 행을 삭제한 뒤 기동하거나,
-- 빈 DB에서 flyway clean 후 migrate 하세요.
-- =============================================================================

-- ── DROP (의존 순서: 레코드 → 목표들 → 마스터) ─────────────────────────────
DROP TABLE IF EXISTS tb_cs_satisfaction_record CASCADE;
DROP TABLE IF EXISTS "TB_CS_SATISFACTION_RECORD" CASCADE;

DROP TABLE IF EXISTS tb_cs_satisfaction_skill_target CASCADE;
DROP TABLE IF EXISTS tb_cs_satisfaction_annual_target CASCADE;
DROP TABLE IF EXISTS tb_cs_satisfaction_dept_monthly_target CASCADE;
DROP TABLE IF EXISTS tb_cs_satisfaction_daily_target CASCADE;
DROP TABLE IF EXISTS "TB_CS_SATISFACTION_DAILY_TARGET" CASCADE;

DROP TABLE IF EXISTS tb_cs_dissatisfaction_type CASCADE;
DROP TABLE IF EXISTS "TB_CS_DISSATISFACTION_TYPE" CASCADE;

-- ── 1. 불만족 유형 마스터 (스크립트·호환용, 레코드는 숫자 코드만 저장) ─────
CREATE TABLE tb_cs_dissatisfaction_type (
    type_id       BIGSERIAL PRIMARY KEY,
    type_code     VARCHAR(64)  NOT NULL,
    type_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_dissat_code UNIQUE (type_code),
    CONSTRAINT uq_cs_dissat_name UNIQUE (type_name)
);

-- ── 2. 부서별 월간 목표 (second_depth_dept_id: 예 5번·6번 센터) ─────────────
CREATE TABLE tb_cs_satisfaction_dept_monthly_target (
    id                    BIGSERIAL PRIMARY KEY,
    target_date           DATE         NOT NULL,
    second_depth_dept_id  INTEGER      NOT NULL,
    target_percent        NUMERIC(6,2) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cs_dept_monthly_target UNIQUE (target_date, second_depth_dept_id)
);

CREATE INDEX idx_cs_dept_monthly_target_date_dept
    ON tb_cs_satisfaction_dept_monthly_target (second_depth_dept_id, target_date);

COMMENT ON TABLE tb_cs_satisfaction_dept_monthly_target IS 'CS 만족도 부서별 월간 목표';

-- ── 3. 스킬별 월간 목표 ────────────────────────────────────────────────────
CREATE TABLE tb_cs_satisfaction_skill_target (
    id                BIGSERIAL PRIMARY KEY,
    target_date       DATE         NOT NULL,
    skill_name        VARCHAR(50)  NOT NULL,
    target_percent    NUMERIC(6,2) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uq_cs_skill_target_date_skill UNIQUE (target_date, skill_name)
);

CREATE INDEX idx_cs_skill_target_date
    ON tb_cs_satisfaction_skill_target (target_date);

COMMENT ON TABLE tb_cs_satisfaction_skill_target IS 'CS 만족도 스킬별 월간 목표 (리텐션, 일반, 이관, 멀티/기술)';

-- ── 4. 중점추진과제 연간 목표 ──────────────────────────────────────────────
CREATE TABLE tb_cs_satisfaction_annual_target (
    id                BIGSERIAL PRIMARY KEY,
    target_year       INTEGER      NOT NULL,
    task_code         VARCHAR(50)  NOT NULL,
    task_name         VARCHAR(100) NOT NULL,
    target_percent    NUMERIC(6,2) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uq_cs_annual_target_year_code UNIQUE (target_year, task_code)
);

CREATE INDEX idx_cs_annual_target_year
    ON tb_cs_satisfaction_annual_target (target_year);

COMMENT ON TABLE tb_cs_satisfaction_annual_target IS 'CS 만족도 중점추진과제 연간 목표 (5대도시, 5060, 문제해결)';

-- ── 5. 만족도 레코드 (엑셀 열 순서와 동일한 컬럼 구성) ─────────────────────
CREATE TABLE tb_cs_satisfaction_record (
    id                       BIGSERIAL PRIMARY KEY,
    subsidiary_type          VARCHAR(100),
    center_name              VARCHAR(100),
    group_name               VARCHAR(100),
    room_name                VARCHAR(100),
    skid                     VARCHAR(64)  NOT NULL,
    eval_date                DATE         NOT NULL,
    consult_time             VARCHAR(20),
    consult_type1            VARCHAR(200),
    consult_type2            VARCHAR(200),
    consult_type3            VARCHAR(200),
    dissatisfaction_type     SMALLINT,
    skill                    VARCHAR(200),
    good_ment                TEXT,
    bad_ment                 TEXT,
    satisfied_yn             CHAR(1)      NOT NULL,
    five_major_cities_yn     CHAR(1),
    gen_5060_yn              CHAR(1),
    score                    NUMERIC(10,2),
    problem_resolved_yn      CHAR(1),
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cs_sat_record_eval_date ON tb_cs_satisfaction_record (eval_date);
CREATE INDEX idx_cs_sat_record_skid ON tb_cs_satisfaction_record (skid);
CREATE INDEX idx_cs_sat_record_date_skid ON tb_cs_satisfaction_record (eval_date, skid);
CREATE INDEX idx_cs_sat_record_center_name ON tb_cs_satisfaction_record (center_name);

COMMENT ON TABLE tb_cs_satisfaction_record IS 'CS 만족도 상담 레코드 (엑셀 업로드)';
