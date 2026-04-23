package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_you_cs")
public class TbCsSatisfactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "\"자회사구분\"", length = 100)
    private String subsidiaryType;

    @Column(name = "\"상담사id\"", nullable = false, length = 64)
    private String skid;

    @Column(name = "\"상담일시\"", nullable = false)
    private LocalDateTime evalDate;

    @Column(name = "\"상담유형1\"", length = 200)
    private String consultType1;

    @Column(name = "\"상담유형2\"", length = 200)
    private String consultType2;

    @Column(name = "\"상담유형3\"", length = 200)
    private String consultType3;

    @Column(name = "\"불만족유형\"")
    private Integer dissatisfactionType;

    @Column(name = "\"스킬\"", length = 200)
    private String skill;

    @Column(name = "\"긍정코멘트\"", columnDefinition = "text")
    private String goodMent;

    @Column(name = "\"부정코멘트\"", columnDefinition = "text")
    private String badMent;

    @Column(name = "\"만족여부\"", nullable = false, length = 1)
    private String satisfiedYn;

    @Column(name = "\"오대도시\"", length = 1)
    private String fiveMajorCitiesYn;

    @Column(name = "\"오공육공\"", length = 1)
    private String gen5060Yn;

    @Column(name = "\"문제해결\"", length = 1)
    private String problemResolvedYn;

    @Column(name = "\"업로드일자\"", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getConsultTime() {
        if (evalDate == null) {
            return null;
        }
        return evalDate.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
