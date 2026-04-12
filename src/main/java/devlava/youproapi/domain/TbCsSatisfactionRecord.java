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
@Table(name = "tb_cs_satisfaction_record")
public class TbCsSatisfactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "eval_date", nullable = false)
    private LocalDate evalDate;

    @Column(name = "skid", nullable = false, length = 64)
    private String skid;

    @Column(name = "satisfied_yn", nullable = false, length = 1)
    private String satisfiedYn;

    @Column(name = "score", precision = 10, scale = 2)
    private BigDecimal score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dissatisfaction_type_id")
    private TbCsDissatisfactionType dissatisfactionType;

    @Column(name = "five_major_cities_yn", length = 1)
    private String fiveMajorCitiesYn;

    @Column(name = "gen_5060_yn", length = 1)
    private String gen5060Yn;

    @Column(name = "problem_resolved_yn", length = 1)
    private String problemResolvedYn;

    @Column(name = "good_ment", columnDefinition = "text")
    private String goodMent;

    @Column(name = "bad_ment", columnDefinition = "text")
    private String badMent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
