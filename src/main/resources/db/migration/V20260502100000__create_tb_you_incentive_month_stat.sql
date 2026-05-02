-- 스케줄러 실행 시점별 평가대상자(YOU 프로 대상) 인원 스냅샷 (통계용)
-- processMonth 한 번 실행당 해당 연·월에 대해 1행 갱신

CREATE TABLE IF NOT EXISTS tb_you_incentive_month_stat (
    reflect_year       INTEGER   NOT NULL,
    reflect_month      INTEGER   NOT NULL,
    eval_target_count  INTEGER   NOT NULL,
    processed_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_you_incentive_month_stat PRIMARY KEY (reflect_year, reflect_month)
);
