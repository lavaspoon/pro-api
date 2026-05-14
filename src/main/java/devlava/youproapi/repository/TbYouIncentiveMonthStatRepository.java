package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveMonthStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TbYouIncentiveMonthStatRepository
        extends JpaRepository<TbYouIncentiveMonthStat, TbYouIncentiveMonthStat.Pk> {

    /**
     * 해당 연·센터·월 구간에 저장된 평가대상자 스냅샷 — 관리자 대시보드 인증률 분모(월별 평균 인원).
     */
    List<TbYouIncentiveMonthStat> findByReflectYearAndSecondDepthDeptIdAndReflectMonthBetweenOrderByReflectMonth(
            Integer reflectYear,
            Integer secondDepthDeptId,
            int reflectMonthStart,
            int reflectMonthEnd);

    Optional<TbYouIncentiveMonthStat> findByReflectYearAndReflectMonthAndSecondDepthDeptId(
            Integer reflectYear, Integer reflectMonth, Integer secondDepthDeptId);
}
