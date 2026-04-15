package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_skill_target")
public class TbCsSatisfactionSkillTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 월 단위 목표 (매월 1일) */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** 스킬명: 리텐션, 일반, 이관, 멀티/기술 */
    @Column(name = "skill_name", nullable = false, length = 50)
    private String skillName;

    /** 목표 퍼센트 */
    @Column(name = "target_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal targetPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
