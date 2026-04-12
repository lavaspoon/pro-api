package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 월별 목표%. {@code target_date} 는 항상 해당 월의 1일(센터당 월 1행). 테이블명은 레거시.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_daily_target")
public class TbCsSatisfactionDailyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 그 달 1일로 정규화하여 저장 (DB 컬럼명은 레거시). */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "second_depth_dept_id", nullable = false)
    private Integer secondDepthDeptId;

    @Column(name = "target_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
