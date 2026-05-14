-- 평가대상자 스냅샷을 2depth 센터(루트)별로 분리 저장 (연간 인증률 분모를 센터 기준으로 계산)
-- 기존 전체 집계 행은 폐기 후 PK 재구성

DELETE FROM tb_you_incentive_month_stat;

ALTER TABLE tb_you_incentive_month_stat DROP CONSTRAINT pk_you_incentive_month_stat;

ALTER TABLE tb_you_incentive_month_stat
    ADD COLUMN second_depth_dept_id INTEGER NOT NULL;

ALTER TABLE tb_you_incentive_month_stat
    ADD CONSTRAINT pk_you_incentive_month_stat
        PRIMARY KEY (reflect_year, reflect_month, second_depth_dept_id);

COMMENT ON COLUMN tb_you_incentive_month_stat.second_depth_dept_id IS
    'TB_LMS_DEPT.dept_id — youpro.admin.second-depth-dept-ids 에 정의된 센터(최상위) 루트';
