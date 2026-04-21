-- SQL Server(MSSQL) 전용 스키마
-- CS 만족도 + 평가대상자

IF OBJECT_ID('dbo.tb_cs_dissatisfaction_type', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_cs_dissatisfaction_type (
        type_id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        type_code VARCHAR(64) NOT NULL,
        type_name VARCHAR(200) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT uq_cs_dissat_code UNIQUE (type_code),
        CONSTRAINT uq_cs_dissat_name UNIQUE (type_name)
    );
END;

IF OBJECT_ID('dbo.tb_cs_satisfaction_dept_monthly_target', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_cs_satisfaction_dept_monthly_target (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        target_date DATE NOT NULL,
        second_depth_dept_id INT NOT NULL,
        target_percent DECIMAL(6,2) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT uq_cs_dept_monthly_target UNIQUE (target_date, second_depth_dept_id)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_cs_dept_monthly_target_date_dept'
      AND object_id = OBJECT_ID('dbo.tb_cs_satisfaction_dept_monthly_target')
)
BEGIN
    CREATE INDEX idx_cs_dept_monthly_target_date_dept
        ON dbo.tb_cs_satisfaction_dept_monthly_target (second_depth_dept_id, target_date);
END;

IF OBJECT_ID('dbo.tb_cs_satisfaction_skill_target', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_cs_satisfaction_skill_target (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        target_date DATE NOT NULL,
        skill_name VARCHAR(50) NOT NULL,
        target_percent DECIMAL(6,2) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        CONSTRAINT uq_cs_skill_target_date_skill UNIQUE (target_date, skill_name)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_cs_skill_target_date'
      AND object_id = OBJECT_ID('dbo.tb_cs_satisfaction_skill_target')
)
BEGIN
    CREATE INDEX idx_cs_skill_target_date
        ON dbo.tb_cs_satisfaction_skill_target (target_date);
END;

IF OBJECT_ID('dbo.tb_cs_satisfaction_annual_target', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_cs_satisfaction_annual_target (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        target_year INT NOT NULL,
        task_code VARCHAR(50) NOT NULL,
        task_name VARCHAR(100) NOT NULL,
        target_percent DECIMAL(6,2) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        CONSTRAINT uq_cs_annual_target_year_code UNIQUE (target_year, task_code)
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_cs_annual_target_year'
      AND object_id = OBJECT_ID('dbo.tb_cs_satisfaction_annual_target')
)
BEGIN
    CREATE INDEX idx_cs_annual_target_year
        ON dbo.tb_cs_satisfaction_annual_target (target_year);
END;

IF OBJECT_ID('dbo.tb_you_cs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.tb_you_cs (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        [자회사구분] VARCHAR(MAX) NULL,
        [상담사id] VARCHAR(MAX) NOT NULL,
        [상담일자] VARCHAR(MAX) NOT NULL,
        [상담시간] VARCHAR(MAX) NULL,
        [상담유형1] VARCHAR(MAX) NULL,
        [상담유형2] VARCHAR(MAX) NULL,
        [상담유형3] VARCHAR(MAX) NULL,
        [불만족유형] SMALLINT NULL,
        [스킬] VARCHAR(MAX) NULL,
        [긍정코멘트] VARCHAR(MAX) NULL,
        [부정코멘트] VARCHAR(MAX) NULL,
        [만족여부] VARCHAR(MAX) NOT NULL,
        [5대도시] VARCHAR(MAX) NULL,
        [5060] VARCHAR(MAX) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_tb_you_cs_상담일자'
      AND object_id = OBJECT_ID('dbo.tb_you_cs')
)
BEGIN
    CREATE INDEX idx_tb_you_cs_상담일자
        ON dbo.tb_you_cs ([상담일자]);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_tb_you_cs_상담사id'
      AND object_id = OBJECT_ID('dbo.tb_you_cs')
)
BEGIN
    CREATE INDEX idx_tb_you_cs_상담사id
        ON dbo.tb_you_cs ([상담사id]);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_tb_you_cs_상담일자_상담사id'
      AND object_id = OBJECT_ID('dbo.tb_you_cs')
)
BEGIN
    CREATE INDEX idx_tb_you_cs_상담일자_상담사id
        ON dbo.tb_you_cs ([상담일자], [상담사id]);
END;

IF OBJECT_ID('dbo.TB_YOU_TARGET', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.TB_YOU_TARGET (
        [회사] VARCHAR(MAX) NULL,
        [센터] VARCHAR(MAX) NULL,
        [상담사ID] VARCHAR(MAX) NULL,
        [문서보안ID] VARCHAR(MAX) NULL,
        [성명] VARCHAR(MAX) NULL,
        [그룹] VARCHAR(MAX) NULL,
        [실] VARCHAR(MAX) NULL,
        [스킬] VARCHAR(MAX) NULL,
        [직책] VARCHAR(MAX) NULL,
        [평가대상여부] VARCHAR(MAX) NULL
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'idx_tb_you_target_상담사id'
      AND object_id = OBJECT_ID('dbo.TB_YOU_TARGET')
)
BEGIN
    CREATE INDEX idx_tb_you_target_상담사id
        ON dbo.TB_YOU_TARGET ([상담사ID]);
END;
