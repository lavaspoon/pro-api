package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveReflect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TbYouIncentiveReflectRepository extends JpaRepository<TbYouIncentiveReflect, Long> {

    Optional<TbYouIncentiveReflect> findBySkidAndReflectYearAndReflectMonth(
            String skid, Integer reflectYear, Integer reflectMonth);

    List<TbYouIncentiveReflect> findBySkidAndReflectYearOrderByReflectMonth(
            String skid, Integer reflectYear);

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
}
