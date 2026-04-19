package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouProCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TbYouProCaseRepository extends JpaRepository<TbYouProCase, Long> {

    /** 특정 구성원의 전체 사례 (최신순) */
    List<TbYouProCase> findBySkidOrderBySubmittedAtDesc(String skid);

    /** 특정 구성원 + 상태별 사례 */
    List<TbYouProCase> findBySkidAndStatus(String skid, String status);

    /** 전체 검토 대기(pending) 사례 (오래된 것 먼저) */
    List<TbYouProCase> findByStatusOrderBySubmittedAtAsc(String status);

    /** 특정 구성원 집합의 검토 대기 사례 */
    List<TbYouProCase> findByStatusAndSkidInOrderBySubmittedAtAsc(String status, Collection<String> skids);

    /**
     * 통화일시({@code call_date}) 문자열 앞 10자 이상 — {@code yyyy-MM-dd ...} 형식 가정.
     * 월은 {@code :monthStr} 두 자리({@code 01}–{@code 12})와 비교.
     */
    // ─── 연도·월은 call_date 기준 (접수 시각 submitted_at 아님) ───────────────

    /** 연도별 접수(신청) 건수 — 상태 무관, call_date 연도가 일치 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countSubmittedByYear(@Param("yearStr") String yearStr);

    /** 연도별 선정 건수 (전체) — status = selected, call_date 연도 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countSelectedByYear(@Param("yearStr") String yearStr);

    /** 전 센터 월별 신청(접수) 건수 — call_date 연·월 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           """)
    long countSubmittedByYearMonth(@Param("yearStr") String yearStr, @Param("monthStr") String monthStr);

    /** 전 센터 월별 선정 건수 — call_date 연·월, status = selected */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           """)
    long countSelectedByYearMonth(@Param("yearStr") String yearStr, @Param("monthStr") String monthStr);

    /** 구성원별 연간 선정 건수 — call_date 연도 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid = :skid
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countSelectedBySkidAndYear(@Param("skid") String skid, @Param("yearStr") String yearStr);

    /** 구성원 월간 선정 건수 — call_date 연·월 (월간 선정 한도 등과 동일 기준) */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid = :skid
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           """)
    long countSelectedBySkidAndYearMonth(
            @Param("skid") String skid,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);

    /** 구성원 월간 검토 대기 건수 */
    long countBySkidAndStatus(String skid, String status);

    /** 특정 실(deptIdx) 소속 구성원들의 사례 목록 */
    @Query("""
           SELECT c FROM TbYouProCase c
           WHERE c.skid IN :skids
           ORDER BY c.submittedAt DESC
           """)
    List<TbYouProCase> findBySkidIn(@Param("skids") List<String> skids);

    /** 소속 구성원 기준, 해당 연도 판정 완료(선정+비선정) 건수 — call_date 연도 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status IN ('selected', 'rejected')
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countJudgedBySkidsInYear(@Param("skids") List<String> skids, @Param("yearStr") String yearStr);

    /** 소속 구성원 기준, 해당 연·월(call_date) 판정 완료(선정·비선정) 건수 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status IN ('selected', 'rejected')
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           """)
    long countJudgedBySkidsAndYearMonth(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);

    // ─── 관리자 스코프(구성원 IN) 배치 통계 — N+1 방지 ───────────────────────

    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countSubmittedBySkidsAndYear(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    /** 소속 구성원 기준, 해당 연·월 접수 건수(상태 무관) — call_date 연·월 */
    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           """)
    long countSubmittedBySkidsAndYearMonth(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);

    @Query("""
           SELECT COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           """)
    long countSelectedBySkidsAndYear(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    @Query("""
           SELECT SUBSTRING(c.callDate, 6, 2), COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           GROUP BY SUBSTRING(c.callDate, 6, 2)
           ORDER BY SUBSTRING(c.callDate, 6, 2)
           """)
    List<Object[]> countSubmittedBySkidsGroupByMonth(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    @Query("""
           SELECT SUBSTRING(c.callDate, 6, 2), COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           GROUP BY SUBSTRING(c.callDate, 6, 2)
           ORDER BY SUBSTRING(c.callDate, 6, 2)
           """)
    List<Object[]> countSelectedBySkidsGroupByMonth(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           GROUP BY c.skid
           """)
    List<Object[]> countSubmittedBySkidsAndYearGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           GROUP BY c.skid
           """)
    List<Object[]> countSubmittedBySkidsAndYearMonthGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           GROUP BY c.skid
           """)
    List<Object[]> countSelectedBySkidsAndYearGroupBySkid(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'selected'
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
             AND SUBSTRING(c.callDate, 6, 2) = :monthStr
           GROUP BY c.skid
           """)
    List<Object[]> countSelectedBySkidsAndYearMonthGroupBySkid(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status = 'pending'
           GROUP BY c.skid
           """)
    List<Object[]> countPendingBySkidsGroupBySkid(@Param("skids") Collection<String> skids);

    @Query("""
           SELECT c.skid, COUNT(c) FROM TbYouProCase c
           WHERE c.skid IN :skids
             AND c.status IN ('selected', 'rejected')
             AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
             AND SUBSTRING(c.callDate, 1, 4) = :yearStr
           GROUP BY c.skid
           """)
    List<Object[]> countJudgedBySkidsAndYearGroupBySkid(@Param("skids") Collection<String> skids, @Param("yearStr") String yearStr);

    // ─── 관리자 대시보드 통계 배치 (스코프 skids) ─────────────────────────────

    /**
     * 연도·스코프 기준 월별 접수(전체) / 선정 건수를 한 번에 집계.
     * 월 키는 call_date의 MM 문자열(01–12) — 서비스에서 1–12 정수로 변환.
     */
    @Query("""
            SELECT SUBSTRING(c.callDate, 6, 2), COUNT(c),
                   SUM(CASE WHEN c.status = 'selected' THEN 1 ELSE 0 END)
            FROM TbYouProCase c
            WHERE c.skid IN :skids
              AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
              AND SUBSTRING(c.callDate, 1, 4) = :yearStr
            GROUP BY SUBSTRING(c.callDate, 6, 2)
            ORDER BY SUBSTRING(c.callDate, 6, 2)
            """)
    List<Object[]> aggregateMonthlySubmittedAndSelectedBySkidsAndYear(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr);

    /**
     * 스코프 구성원별 선정(연/월)·대기·연도판정 건수를 한 번에 집계.
     * 선정·연도 판정 건수는 call_date 기준.
     */
    @Query("""
            SELECT c.skid,
                   SUM(CASE WHEN c.status = 'selected'
                            AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
                            AND SUBSTRING(c.callDate, 1, 4) = :yearStr THEN 1 ELSE 0 END),
                   SUM(CASE WHEN c.status = 'selected'
                            AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
                            AND SUBSTRING(c.callDate, 1, 4) = :yearStr
                            AND SUBSTRING(c.callDate, 6, 2) = :monthStr THEN 1 ELSE 0 END),
                   SUM(CASE WHEN c.status = 'pending' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN c.status IN ('selected', 'rejected')
                            AND c.callDate IS NOT NULL AND LENGTH(TRIM(c.callDate)) >= 10
                            AND SUBSTRING(c.callDate, 1, 4) = :yearStr THEN 1 ELSE 0 END)
            FROM TbYouProCase c
            WHERE c.skid IN :skids
            GROUP BY c.skid
            """)
    List<Object[]> aggregatePerSkidDashboardMetrics(
            @Param("skids") Collection<String> skids,
            @Param("yearStr") String yearStr,
            @Param("monthStr") String monthStr);
}
