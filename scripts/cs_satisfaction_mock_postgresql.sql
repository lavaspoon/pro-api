-- =============================================================================
-- CS 만족도 테스트용 Mock 데이터 (PostgreSQL)
-- tb_cs_* 테이블이 Flyway(V20260414000000) 적용 후 실행하세요.
--
-- 집계 대상: TB_LMS_MEMBER 중 use_yn='Y' 이고 dept_idx 가 관리자 스코프(leaf)에 포함된 구성원만
--           CsSatisfactionService 가 반영합니다. 구성원이 없으면 화면 집계는 0입니다.
-- =============================================================================

-- 0) 이전 mock 제거(같은 스크립트를 여러 번 돌려도 대략 깨끗하게)
DELETE FROM tb_cs_satisfaction_record WHERE good_ment = 'MOCK_TEST_CS_SAT';
DELETE FROM tb_cs_satisfaction_dept_monthly_target WHERE target_percent IN (90.00, 88.00)
  AND target_date >= date_trunc('year', CURRENT_DATE)::date
  AND target_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date;

-- 1) 불만족 유형
INSERT INTO tb_cs_dissatisfaction_type (type_code, type_name)
SELECT v.code, v.name
FROM (VALUES
  ('MOCK_WAIT', '대기시간'),
  ('MOCK_RESOLVE', '문제 미해결')
) AS v(code, name)
WHERE NOT EXISTS (SELECT 1 FROM tb_cs_dissatisfaction_type t WHERE t.type_code = v.code);

-- 2) 올해 월간 목표(%) — target_date 는 매월 1일. second_depth_dept_id 는 yml second-depth-dept-ids 와 맞출 것 (예: 5, 6)
INSERT INTO tb_cs_satisfaction_dept_monthly_target (target_date, second_depth_dept_id, target_percent)
SELECT d::date, 5, 90.00
FROM generate_series(
  date_trunc('year', CURRENT_DATE)::date,
  (date_trunc('year', CURRENT_DATE) + interval '11 months')::date,
  interval '1 month'
) AS g(d);

INSERT INTO tb_cs_satisfaction_dept_monthly_target (target_date, second_depth_dept_id, target_percent)
SELECT d::date, 6, 88.00
FROM generate_series(
  date_trunc('year', CURRENT_DATE)::date,
  (date_trunc('year', CURRENT_DATE) + interval '11 months')::date,
  interval '1 month'
) AS g(d);

-- 3) 만족도 원장 — 스코프 내 구성원 최대 6명 × 5건(올해 임의 일)
INSERT INTO tb_cs_satisfaction_record (
  eval_date, skid, satisfied_yn,
  dissatisfaction_type, five_major_cities_yn, gen_5060_yn, problem_resolved_yn,
  good_ment, bad_ment
)
SELECT
  (date_trunc('year', CURRENT_DATE) + ((10 + floor(random() * 340))::int || ' days')::interval)::date,
  m.skid,
  CASE WHEN random() < 0.82 THEN 'Y' ELSE 'N' END,
  CASE WHEN random() < 0.2 THEN 1 + floor(random() * 5)::int ELSE NULL END,
  'N',
  'N',
  'Y',
  'MOCK_TEST_CS_SAT',
  CASE WHEN random() < 0.18 THEN '모의 불만 멘트' ELSE NULL END
FROM (
  SELECT skid
  FROM "TB_LMS_MEMBER"
  WHERE use_yn = 'Y' AND skid IS NOT NULL
  ORDER BY skid
  LIMIT 6
) m
CROSS JOIN generate_series(1, 5) AS s(n);

-- 확인
-- SELECT COUNT(*) FROM tb_cs_satisfaction_record WHERE good_ment = 'MOCK_TEST_CS_SAT';
-- SELECT * FROM tb_cs_satisfaction_dept_monthly_target ORDER BY target_date, second_depth_dept_id;
