-- 인센티브 반영 이력 테이블
-- 스케줄러가 매달 1일 전달 CS 만족도 달성 여부를 확인하고 선정건수를 반영한 결과를 기록한다.

CREATE TABLE IF NOT EXISTS tb_you_incentive_reflect (
    id              BIGSERIAL PRIMARY KEY,

    -- 대상 구성원
    skid            VARCHAR(50)  NOT NULL,

    -- 반영 대상 연·월 (1~9월)
    reflect_year    INTEGER      NOT NULL,
    reflect_month   INTEGER      NOT NULL,

    -- CS 만족도 목표 달성 여부 ('Y' / 'N')
    cs_target_met   CHAR(1)      NOT NULL DEFAULT 'N',

    -- 해당 월 실제 선정 건수 (TbYouProCase.status = 'selected')
    selected_count_raw  INTEGER  NOT NULL DEFAULT 0,

    -- 실적 반영 건수 (cs_target_met='N' 이면 0)
    reflected_count     INTEGER  NOT NULL DEFAULT 0,

    -- 해당 연도 누적 반영 건수 (이달 포함)
    cumulative_count    INTEGER  NOT NULL DEFAULT 0,

    -- 누적 건수 기준 등급에 따른 이달 지급 예정 금액
    monthly_payout_won  INTEGER  NOT NULL DEFAULT 0,

    -- 처리 일시
    processed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_incentive_reflect_skid_ym UNIQUE (skid, reflect_year, reflect_month)
);

CREATE INDEX IF NOT EXISTS idx_incentive_reflect_skid_year
    ON tb_you_incentive_reflect (skid, reflect_year);

CREATE INDEX IF NOT EXISTS idx_incentive_reflect_year
    ON tb_you_incentive_reflect (reflect_year);
