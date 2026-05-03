package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveReflect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TbYouIncentiveReflectRepository extends JpaRepository<TbYouIncentiveReflect, Long> {

    Optional<TbYouIncentiveReflect> findBySkidAndReflectYearAndReflectMonth(
            String skid, Integer reflectYear, Integer reflectMonth);

    List<TbYouIncentiveReflect> findBySkidAndReflectYearOrderByReflectMonth(
            String skid, Integer reflectYear);

    /**
     * 해당 연도에서 {@code reflect_month} 가 가장 큰 행(가장 최근 인센티브 반영 월) 1건.
     * 그 행의 {@link TbYouIncentiveReflect#getCumulativeCount()} 가 화면 누적 건수 소스다.
     */
    Optional<TbYouIncentiveReflect> findFirstBySkidAndReflectYearOrderByReflectMonthDesc(
            String skid, Integer reflectYear);

    /** 관리자 랭킹 등 — 해당 연·스코프 skid 의 모든 반영 행 (월별 중 최신 cumulative 선택용) */
    List<TbYouIncentiveReflect> findByReflectYearAndSkidIn(
            Integer reflectYear, Collection<String> skids);

    /** 특정 연도·월 이전의 누적 반영 건수 합계 (해당 월 미포함) */
    @Query("""
           SELECT COALESCE(SUM(r.reflectedCount), 0)
           FROM TbYouIncentiveReflect r
           WHERE r.skid = :skid
             AND r.reflectYear = :year
             AND r.reflectMonth < :month
           """)
    int sumReflectedCountBeforeMonth(
            @Param("skid") String skid,
            @Param("year") int year,
            @Param("month") int month);

    /** 특정 구성원의 연간 누적 반영 건수 합계 */
    @Query("""
           SELECT COALESCE(SUM(r.reflectedCount), 0)
           FROM TbYouIncentiveReflect r
           WHERE r.skid = :skid
             AND r.reflectYear = :year
           """)
    long sumReflectedCountForYear(@Param("skid") String skid, @Param("year") int year);

    /** 특정 구성원의 연간 지급 예정 금액 합계 */
    @Query("""
           SELECT COALESCE(SUM(r.monthlyPayoutWon), 0)
           FROM TbYouIncentiveReflect r
           WHERE r.skid = :skid
             AND r.reflectYear = :year
           """)
    long sumMonthlyPayoutWonForYear(@Param("skid") String skid, @Param("year") int year);

    /** 연도 기준 구성원별 누적 반영 건수 집계 (전체 구성원 — 순위/topSelected 계산용) */
    @Query("""
           SELECT r.skid, SUM(r.reflectedCount)
           FROM TbYouIncentiveReflect r
           WHERE r.reflectYear = :year
           GROUP BY r.skid
           """)
    List<Object[]> sumReflectedCountGroupBySkidForYear(@Param("year") int year);

    /**
     * 해당 연도에 {@code cumulative_count >= 1} 인 행이 한 번이라도 있는 스코프 내 구성원 수.
     * 관리자 대시보드 「연간 인증률」 분자.
     */
    @Query("""
           SELECT COUNT(DISTINCT r.skid)
           FROM TbYouIncentiveReflect r
           WHERE r.reflectYear = :year
             AND r.cumulativeCount >= 1
             AND r.skid IN :skids
           """)
    long countDistinctSkidsCertifiedForYear(
            @Param("year") int year,
            @Param("skids") Collection<String> skids);
}
