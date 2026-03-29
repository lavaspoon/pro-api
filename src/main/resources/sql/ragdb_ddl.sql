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
-- 2. TB_YOU_PRO_CASE
--    우수 상담 사례 접수 및 판정 내역
-- =============================================================================
CREATE TABLE IF NOT EXISTS TB_YOU_PRO_CASE (
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
    ai_key_phrase    TEXT,                                   -- AI가 추출한 STT 중 핵심 멘트
    ai_key_point     TEXT,                                   -- AI가 전하는 피드백 (JSON 등)
    CONSTRAINT PK_TB_YOU_PRO_CASE PRIMARY KEY (case_id)
);

COMMENT ON TABLE  TB_YOU_PRO_CASE                  IS 'YouPro 우수 상담 사례';
COMMENT ON COLUMN TB_YOU_PRO_CASE.case_id          IS '사례 고유 ID (BIGSERIAL)';
COMMENT ON COLUMN TB_YOU_PRO_CASE.skid             IS '접수자 SK ID';
COMMENT ON COLUMN TB_YOU_PRO_CASE.title            IS '사례 제목';
COMMENT ON COLUMN TB_YOU_PRO_CASE.description      IS '응대 내용 요약';
COMMENT ON COLUMN TB_YOU_PRO_CASE.submitted_at     IS '접수 시각';
COMMENT ON COLUMN TB_YOU_PRO_CASE.status           IS '상태 (pending | selected | rejected)';
COMMENT ON COLUMN TB_YOU_PRO_CASE.call_date        IS '통화 일시 - STT TB_YOU_PRO_STT.reg_date·skid 매칭용 (예: 2026-03-05 16:00:00)';
COMMENT ON COLUMN TB_YOU_PRO_CASE.customer_type    IS '고객 유형';
COMMENT ON COLUMN TB_YOU_PRO_CASE.judgment_reason  IS '판정 사유';
COMMENT ON COLUMN TB_YOU_PRO_CASE.judged_at        IS '판정 시각';
COMMENT ON COLUMN TB_YOU_PRO_CASE.judged_by        IS '판정자 SK ID';
COMMENT ON COLUMN TB_YOU_PRO_CASE.ai_key_phrase    IS 'AI가 추출한 STT 중 핵심 멘트';
COMMENT ON COLUMN TB_YOU_PRO_CASE.ai_key_point      IS 'AI가 전하는 피드백';

CREATE INDEX IF NOT EXISTS IDX_YOU_PRO_CASE_SKID   ON TB_YOU_PRO_CASE (skid);
CREATE INDEX IF NOT EXISTS IDX_YOU_PRO_CASE_STATUS ON TB_YOU_PRO_CASE (status);
CREATE INDEX IF NOT EXISTS IDX_YOU_PRO_CASE_MONTH  ON TB_YOU_PRO_CASE (skid, status, submitted_at);

-- 기존 TB_YOUPRO_CASE 마이그레이션: ragdb_migration_tb_you_pro_case.sql 참고

-- =============================================================================
-- 3. TB_YOU_PRO_STT
--    STT 전사 (skid + reg_date 숫자열이 사례 skid·call_date 와 매칭)
--    [참고] 운영 DB가 SQL Server 인 경우 동일 스키마로 테이블 생성 후 연동
-- =============================================================================
CREATE TABLE IF NOT EXISTS "TB_YOU_PRO_STT" (
    stt_id    BIGSERIAL       NOT NULL,
    skid      VARCHAR(30),
    reg_date  VARCHAR(30),
    stt       TEXT,
    CONSTRAINT PK_TB_YOU_PRO_STT PRIMARY KEY (stt_id)
);

COMMENT ON TABLE  "TB_YOU_PRO_STT" IS 'STT 전사 (유선 유프로)';
COMMENT ON COLUMN "TB_YOU_PRO_STT".stt_id   IS '일련번호 (자동 증가)';
COMMENT ON COLUMN "TB_YOU_PRO_STT".skid     IS '상담사 SK ID (사례 skid 와 매칭)';
COMMENT ON COLUMN "TB_YOU_PRO_STT".reg_date IS '등록·통화 일시 문자열 (call_date 와 숫자 정규화 비교)';
COMMENT ON COLUMN "TB_YOU_PRO_STT".stt      IS 'STT 전체 전사';

CREATE INDEX IF NOT EXISTS IDX_TB_YOU_PRO_STT_SKID ON "TB_YOU_PRO_STT" (skid);
