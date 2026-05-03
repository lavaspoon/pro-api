package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveMonthStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbYouIncentiveMonthStatRepository
        extends JpaRepository<TbYouIncentiveMonthStat, TbYouIncentiveMonthStat.Pk> {

    /**
     * YOU PRO 이벤트 월(1~9) 구간에 저장된 스케줄 스냅샷 — 관리자 인증률 분모(월별 평균 인원)용.
     */
    List<TbYouIncentiveMonthStat> findByReflectYearAndReflectMonthBetweenOrderByReflectMonth(
            Integer reflectYear, int reflectMonthStart, int reflectMonthEnd);
}
