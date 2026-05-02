package devlava.youproapi.repository;

import devlava.youproapi.domain.TbYouIncentiveMonthStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbYouIncentiveMonthStatRepository
        extends JpaRepository<TbYouIncentiveMonthStat, TbYouIncentiveMonthStat.Pk> {
}
