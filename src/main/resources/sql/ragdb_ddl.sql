-- =============================================================================
-- Database : ragdb
-- Description : YouPro 우수 상담 사례 관리 테이블
--
-- [사용 방법]
--   psql -h localhost -p 5433 -U raguser -d ragdb -f ragdb_ddl.sql
--
-- [주의]
--   ddl-auto = none 으로 설정되어 있으므로 서버 기동 전 반드시 직접 실행 필요
-- =============================================================================

-- =============================================================================
-- 1. TB_YOUPRO_ROLE
--    구성원 역할 테이블 (관리자 / 담당자)
--    TB_LMS_MEMBER 와 skid 로 연결
-- =============================================================================
CREATE TABLE IF NOT EXISTS TB_YOUPRO_ROLE (
    skid    VARCHAR(50)     NOT NULL,
    role    VARCHAR(20)     NOT NULL DEFAULT '담당자',   -- '관리자' | '담당자'
    CONSTRAINT PK_TB_YOUPRO_ROLE PRIMARY KEY (skid)
);

COMMENT ON TABLE  TB_YOUPRO_ROLE        IS 'YouPro 구성원 역할';
COMMENT ON COLUMN TB_YOUPRO_ROLE.skid   IS '구성원 SK ID (TB_LMS_MEMBER.skid FK)';
COMMENT ON COLUMN TB_YOUPRO_ROLE.role   IS '역할 (관리자 | 담당자)';

-- 샘플 관리자 계정 (실제 SKID 로 교체 필요)
-- INSERT INTO TB_YOUPRO_ROLE (skid, role) VALUES ('USR010', '관리자');

-- =============================================================================
-- 2. TB_YOUPRO_CASE
--    우수 상담 사례 접수 및 판정 내역
-- =============================================================================
CREATE TABLE IF NOT EXISTS TB_YOUPRO_CASE (
    case_id          BIGSERIAL       NOT NULL,
    skid             VARCHAR(50)     NOT NULL,               -- 접수자 SK ID
    title            VARCHAR(200)    NOT NULL,               -- 사례 제목
    description      TEXT            NOT NULL,               -- 응대 내용 요약
    submitted_at     TIMESTAMP       NOT NULL,               -- 접수 시각
    status           VARCHAR(20)     NOT NULL DEFAULT 'pending', -- pending | selected | rejected
    call_date        VARCHAR(50),                            -- 통화 일시 (STT 조회 키)
    customer_type    VARCHAR(100),                           -- 고객 유형
    judgment_reason  TEXT,                                   -- 판정 사유
    judged_at        TIMESTAMP,                              -- 판정 시각
    judged_by        VARCHAR(50),                            -- 판정자 SK ID
    admin_edited_transcript TEXT,                           -- 관리자 확정 STT 대화 텍스트
    ai_snapshot_json      TEXT,                           -- 1차 AI 분석 스냅샷(JSON)
    CONSTRAINT PK_TB_YOUPRO_CASE PRIMARY KEY (case_id)
);

COMMENT ON TABLE  TB_YOUPRO_CASE                  IS 'YouPro 우수 상담 사례';
COMMENT ON COLUMN TB_YOUPRO_CASE.case_id          IS '사례 고유 ID (BIGSERIAL)';
COMMENT ON COLUMN TB_YOUPRO_CASE.skid             IS '접수자 SK ID';
COMMENT ON COLUMN TB_YOUPRO_CASE.title            IS '사례 제목';
COMMENT ON COLUMN TB_YOUPRO_CASE.description      IS '응대 내용 요약';
COMMENT ON COLUMN TB_YOUPRO_CASE.submitted_at     IS '접수 시각';
COMMENT ON COLUMN TB_YOUPRO_CASE.status           IS '상태 (pending | selected | rejected)';
COMMENT ON COLUMN TB_YOUPRO_CASE.call_date        IS '통화 일시 - STT 유선_유프로_STT 일자·상담시간 매칭용 (예: 2026-03-05 16:00:00)';
COMMENT ON COLUMN TB_YOUPRO_CASE.customer_type    IS '고객 유형';
COMMENT ON COLUMN TB_YOUPRO_CASE.judgment_reason  IS '판정 사유';
COMMENT ON COLUMN TB_YOUPRO_CASE.judged_at        IS '판정 시각';
COMMENT ON COLUMN TB_YOUPRO_CASE.judged_by        IS '판정자 SK ID';

CREATE INDEX IF NOT EXISTS IDX_YOUPRO_CASE_SKID   ON TB_YOUPRO_CASE (skid);
CREATE INDEX IF NOT EXISTS IDX_YOUPRO_CASE_STATUS ON TB_YOUPRO_CASE (status);
CREATE INDEX IF NOT EXISTS IDX_YOUPRO_CASE_MONTH  ON TB_YOUPRO_CASE (skid, status, submitted_at);

-- 기존 DB 마이그레이션: 엔티티와 불일치 시 아래 또는 ragdb_migration_add_case_columns.sql 실행
-- ALTER TABLE tb_youpro_case ADD COLUMN IF NOT EXISTS admin_edited_transcript TEXT;
-- ALTER TABLE tb_youpro_case ADD COLUMN IF NOT EXISTS ai_snapshot_json TEXT;

-- =============================================================================
-- 3. 유선_유프로_STT
--    STT 전사 (일자 YYYYMMDD + 상담시간 HHMM 으로 사례 call_date 와 매칭)
--    [참고] 운영 DB가 SQL Server 인 경우 동일 스키마로 테이블 생성 후 연동
-- =============================================================================
CREATE TABLE IF NOT EXISTS "유선_유프로_STT" (
    "Num"        BIGSERIAL    NOT NULL,
    "일자"       VARCHAR(20),
    "STT원본"    TEXT,
    "상담시간"   VARCHAR(20),
    "상담사ID"   VARCHAR(20),
    CONSTRAINT PK_유선_유프로_STT PRIMARY KEY ("Num")
);

COMMENT ON TABLE  "유선_유프로_STT"      IS 'STT 전사 (유선 유프로)';
COMMENT ON COLUMN "유선_유프로_STT"."Num"       IS '일련번호';
COMMENT ON COLUMN "유선_유프로_STT"."일자"      IS '상담일자 (예: 20260221)';
COMMENT ON COLUMN "유선_유프로_STT"."STT원본"   IS 'STT 전체 전사';
COMMENT ON COLUMN "유선_유프로_STT"."상담시간" IS '시각 (예: 1600 = 16시 00분)';
COMMENT ON COLUMN "유선_유프로_STT"."상담사ID" IS '상담사 ID';
