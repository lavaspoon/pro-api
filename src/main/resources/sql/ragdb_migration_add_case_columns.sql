-- 기존 TB_YOUPRO_CASE 테이블에 엔티티 컬럼이 없을 때 1회 실행
--   psql -h localhost -p 5433 -U raguser -d ragdb -f ragdb_migration_add_case_columns.sql

ALTER TABLE tb_youpro_case ADD COLUMN IF NOT EXISTS admin_edited_transcript TEXT;
ALTER TABLE tb_youpro_case ADD COLUMN IF NOT EXISTS ai_snapshot_json TEXT;
