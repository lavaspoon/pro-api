-- =============================================================================
-- Database : st_etc
-- Description : STT(Speech-To-Text) 결과 저장 테이블
--               (발화 단위 세그먼트 테이블 없음 — 전사본·녹취시간만 보관)
--
-- [사용 방법]
--   psql -h localhost -p 5433 -U raguser -d st_etc -f st_etc_ddl.sql
-- =============================================================================

-- ---------------------------------------------------------
-- 기존 테이블 삭제 (개발환경 초기화 시 사용)
-- 운영 환경에서는 주석 처리 필요
-- ---------------------------------------------------------
-- DROP TABLE IF EXISTS TB_STT_SEGMENT CASCADE;
-- DROP TABLE IF EXISTS TB_STT_RESULT;

-- =============================================================================
-- 1. TB_STT_RESULT
--    STT 처리 결과의 통화 단위 정보 (전체 전사본 + 녹취 시간)
--    call_time 은 VARCHAR 로 저장되며, 우수사례 접수 시 조인 키로 사용된다.
-- =============================================================================
CREATE TABLE IF NOT EXISTS TB_STT_RESULT (
    stt_id          VARCHAR(50)     NOT NULL,               -- STT 결과 고유 ID (외부 STT 시스템 채번)
    call_time       VARCHAR(50)     NOT NULL,               -- 통화 시작 시각 (예: '20260305093000', '2026-03-05 09:30:00')
    agent_skid      VARCHAR(50)     NOT NULL,               -- 상담사 SK ID
    customer_no     VARCHAR(100),                           -- 고객 번호 (개인정보 마스킹 후 저장)
    full_transcript TEXT,                                   -- 통화 전체 전사본 (상담사+고객 통합)
    call_duration   VARCHAR(50),                            -- 통화 총 시간 (예: '00:18:32')
    processed_at    TIMESTAMP       NOT NULL DEFAULT NOW(), -- STT 처리 완료 시각
    CONSTRAINT PK_TB_STT_RESULT PRIMARY KEY (stt_id)
);

COMMENT ON TABLE  TB_STT_RESULT                 IS 'STT 처리 결과 (통화 단위 — 전사본·녹취시간)';
COMMENT ON COLUMN TB_STT_RESULT.stt_id          IS 'STT 결과 고유 ID';
COMMENT ON COLUMN TB_STT_RESULT.call_time       IS '통화 시작 시각 (VARCHAR — 포맷 비정형)';
COMMENT ON COLUMN TB_STT_RESULT.agent_skid      IS '상담사 SK ID';
COMMENT ON COLUMN TB_STT_RESULT.customer_no     IS '고객 번호 (마스킹)';
COMMENT ON COLUMN TB_STT_RESULT.full_transcript IS '통화 전체 전사본';
COMMENT ON COLUMN TB_STT_RESULT.call_duration   IS '통화 총 시간 (HH:MM:SS)';
COMMENT ON COLUMN TB_STT_RESULT.processed_at    IS 'STT 처리 완료 시각';

-- 조회 성능을 위한 인덱스
CREATE INDEX IF NOT EXISTS IDX_STT_RESULT_CALL_TIME    ON TB_STT_RESULT (call_time);
CREATE INDEX IF NOT EXISTS IDX_STT_RESULT_AGENT_SKID   ON TB_STT_RESULT (agent_skid);
CREATE INDEX IF NOT EXISTS IDX_STT_RESULT_AGENT_TIME   ON TB_STT_RESULT (agent_skid, call_time);

-- =============================================================================
-- 2. 개발용 샘플 데이터 (선택적 실행)
-- =============================================================================

-- 샘플 STT 결과 (상담사 SKID: EMP001, 통화 2026-03-05 09:30:00)
INSERT INTO TB_STT_RESULT (stt_id, call_time, agent_skid, customer_no, full_transcript, call_duration, processed_at)
VALUES
(
    'STT-20260305-001',
    '20260305093000',
    'EMP001',
    '010-****-5678',
    '안녕하세요, SKT 고객센터입니다. 어떻게 도와드릴까요? / 네, 데이터 요금제 관련해서 문의드리려고요.',
    '00:18:32',
    NOW()
),
(
    'STT-20260305-002',
    '20260305142500',
    'EMP001',
    '010-****-1234',
    '고객님 안녕하세요. 오늘 어떤 문제로 연락 주셨나요? / 인터넷이 자꾸 끊겨서요.',
    '00:12:10',
    NOW()
),
(
    'STT-20260310-001',
    '20260310110000',
    'EMP002',
    '010-****-9999',
    '안녕하세요 고객님. 요금 관련 문의 주셨군요. / 네 맞아요, 지난 달 요금이 왜 이렇게 많이 나왔는지 몰라서요.',
    '00:25:03',
    NOW()
);
