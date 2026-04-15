package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_annual_target")
public class TbCsSatisfactionAnnualTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 목표 연도 (예: 2026) */
    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    /** 과제 코드: FIVE_MAJOR_CITIES, GEN_5060, PROBLEM_RESOLVED */
    @Column(name = "task_code", nullable = false, length = 50)
    private String taskCode;

    /** 과제명: 5대 도시, 5060, 문제해결 */
    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    /** 목표 퍼센트 */
    @Column(name = "target_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
