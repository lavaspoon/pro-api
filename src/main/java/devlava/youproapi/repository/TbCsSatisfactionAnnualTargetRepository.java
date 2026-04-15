package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionAnnualTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TbCsSatisfactionAnnualTargetRepository extends JpaRepository<TbCsSatisfactionAnnualTarget, Long> {

    Optional<TbCsSatisfactionAnnualTarget> findByTargetYearAndTaskCode(Integer targetYear, String taskCode);

    List<TbCsSatisfactionAnnualTarget> findByTargetYear(Integer targetYear);
}
