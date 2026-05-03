package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_satisfaction_eval_exclude_log")
public class TbCsSatisfactionEvalExcludeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "skill_name", nullable = false, length = 50)
    private String skillName;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "excluded_by_skid", length = 64)
    private String excludedBySkid;

    @Column(name = "updated_row_count", nullable = false)
    private int updatedRowCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
