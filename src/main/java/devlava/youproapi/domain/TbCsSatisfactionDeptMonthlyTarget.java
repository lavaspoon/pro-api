package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 부서별 월간 목표%. {@code target_date} 는 항상 해당 월의 1일(부서당 월 1행).
 * 마이그레이션으로 테이블명 변경: tb_cs_satisfaction_dept_monthly_target
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_dept_monthly_target")
public class TbCsSatisfactionDeptMonthlyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 월 단위 목표 (매월 1일) */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** 부서코드 (5번, 6번 등) */
    @Column(name = "second_depth_dept_id", nullable = false)
    private Integer secondDepthDeptId;

    @Column(name = "target_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
