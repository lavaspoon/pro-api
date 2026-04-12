package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionDailyTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TbCsSatisfactionDailyTargetRepository extends JpaRepository<TbCsSatisfactionDailyTarget, Long> {

    List<TbCsSatisfactionDailyTarget> findBySecondDepthDeptIdAndTargetDateBetween(
            Integer secondDepthDeptId,
            LocalDate from,
            LocalDate to);

    Optional<TbCsSatisfactionDailyTarget> findByTargetDateAndSecondDepthDeptId(
            LocalDate targetDate,
            Integer secondDepthDeptId);

    void deleteByTargetDateAndSecondDepthDeptId(LocalDate targetDate, Integer secondDepthDeptId);
}
