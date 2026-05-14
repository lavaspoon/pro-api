package devlava.youproapi.domain;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * YOU PRO 인센티브 월별 스케줄 스냅샷 — {@code tb_you_incentive_month_stat}
 *
 * <p>스케줄러가 전월 반영 처리 시, 설정된 각 2depth 센터별로 집계한 평가대상자(YOU 참여) 인원을 기록한다.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TbYouIncentiveMonthStat.Pk.class)
@Table(name = "tb_you_incentive_month_stat")
public class TbYouIncentiveMonthStat {

    @Id
    @Column(name = "reflect_year", nullable = false)
    private Integer reflectYear;

    @Id
    @Column(name = "reflect_month", nullable = false)
    private Integer reflectMonth;

    /** {@code youpro.admin.second-depth-dept-ids} 에 해당하는 센터(2depth) 루트 부서 ID */
    @Id
    @Column(name = "second_depth_dept_id", nullable = false)
    private Integer secondDepthDeptId;

    /** 해당 센터 서브트리 내 YOU 프로 평가대상자(useYn=Y, youYn=Y) 인원 */
    @Column(name = "eval_target_count", nullable = false)
    private Integer evalTargetCount;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer reflectYear;
        private Integer reflectMonth;
        private Integer secondDepthDeptId;
    }
}
