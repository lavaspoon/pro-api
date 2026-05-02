package devlava.youproapi.domain;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

/**
 * 인센티브 반영 이력 — {@code tb_you_incentive_reflect}
 *
 * <p>스케줄러가 매달 1일 18시(2~10월) 전월 CS 만족도 달성 여부를 확인하고
 * 선정건수 반영 결과를 기록한다.
 *
 * <ul>
 *   <li>CS 만족도 목표 달성 시 : {@code reflectedCount} = {@code selectedCountRaw}</li>
 *   <li>CS 만족도 목표 미달성 시: {@code reflectedCount} = 0, 지급 0</li>
 *   <li>달성했으나 선정 0건: {@code reflectedCount} = 0, 누적 불변, 해당 월 지급 0</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "tb_you_incentive_reflect",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_incentive_reflect_skid_ym",
        columnNames = {"skid", "reflect_year", "reflect_month"}
    ),
    indexes = {
        @Index(name = "idx_incentive_reflect_skid_year", columnList = "skid, reflect_year"),
        @Index(name = "idx_incentive_reflect_year",      columnList = "reflect_year")
    }
)
public class TbYouIncentiveReflect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 구성원 SK ID */
    @Column(name = "skid", nullable = false, length = 50)
    private String skid;

    /** 반영 대상 연도 */
    @Column(name = "reflect_year", nullable = false)
    private Integer reflectYear;

    /** 반영 대상 월 (1~9) */
    @Column(name = "reflect_month", nullable = false)
    private Integer reflectMonth;

    /**
     * CS 만족도 목표 달성 여부.
     * <ul>
     *   <li>{@code "Y"} — 달성 → 선정건수 반영</li>
     *   <li>{@code "N"} — 미달성 → 선정건수 반영 안 됨</li>
     * </ul>
     */
    @Column(name = "cs_target_met", nullable = false, length = 1)
    private String csTargetMet;

    /** 해당 월 실제 선정 건수 (status = 'selected', call_date 기준) */
    @Column(name = "selected_count_raw", nullable = false)
    private Integer selectedCountRaw;

    /** 실적 반영 건수 — csTargetMet='N' 이면 0 */
    @Column(name = "reflected_count", nullable = false)
    private Integer reflectedCount;

    /** 해당 연도 누적 반영 건수 (이달 포함) */
    @Column(name = "cumulative_count", nullable = false)
    private Integer cumulativeCount;

    /** 누적 건수 기준 등급에 따른 해당 월 지급액 — 반영 건수가 1건 이상일 때만 부여 */
    @Column(name = "monthly_payout_won", nullable = false)
    private Integer monthlyPayoutWon;

    /** 처리(스케줄 실행) 일시 */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
