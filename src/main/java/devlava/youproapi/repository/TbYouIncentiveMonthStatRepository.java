package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveMonthStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbYouIncentiveMonthStatRepository
        extends JpaRepository<TbYouIncentiveMonthStat, TbYouIncentiveMonthStat.Pk> {

    /**
     * 해당 연·월 구간에 저장된 평가대상자 스냅샷 — 관리자 대시보드 인증률 분모(월별 평균 인원) 등.
     */
    List<TbYouIncentiveMonthStat> findByReflectYearAndReflectMonthBetweenOrderByReflectMonth(
            Integer reflectYear, int reflectMonthStart, int reflectMonthEnd);
}
