package devlava.youproapi.repository;

import devlava.youproapi.domain.TbCsSatisfactionDeptMonthlyTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TbCsSatisfactionDeptMonthlyTargetRepository extends JpaRepository<TbCsSatisfactionDeptMonthlyTarget, Long> {

    List<TbCsSatisfactionDeptMonthlyTarget> findBySecondDepthDeptIdAndTargetDateBetween(
            Integer secondDepthDeptId,
            LocalDate from,
            LocalDate to);

    Optional<TbCsSatisfactionDeptMonthlyTarget> findByTargetDateAndSecondDepthDeptId(
            LocalDate targetDate,
            Integer secondDepthDeptId);

    void deleteByTargetDateAndSecondDepthDeptId(LocalDate targetDate, Integer secondDepthDeptId);
}
