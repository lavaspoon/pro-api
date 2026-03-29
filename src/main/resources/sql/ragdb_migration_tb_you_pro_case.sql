-- =============================================================================
-- TB_YOUPRO_CASE → TB_YOU_PRO_CASE 테이블·컬럼명 변경 (PostgreSQL 예시)
-- 실제 객체 이름(\dt, \d tb_youpro_case) 확인 후 필요한 문만 실행.
-- 운영 MS-SQL: sp_rename 등으로 동일 스키마로 맞춘 뒤 엔티티와 연동.
-- =============================================================================

-- 테이블 (소문자 식별자 가정)
ALTER TABLE IF EXISTS tb_youpro_case RENAME TO tb_you_pro_case;

ALTER TABLE IF EXISTS tb_you_pro_case RENAME COLUMN admin_edited_transcript TO ai_key_phrase;
ALTER TABLE IF EXISTS tb_you_pro_case RENAME COLUMN ai_snapshot_json TO ai_key_point;

-- 인덱스 이름은 DB마다 다를 수 있음. 예:
-- ALTER INDEX idx_youpro_case_skid RENAME TO idx_you_pro_case_skid;
