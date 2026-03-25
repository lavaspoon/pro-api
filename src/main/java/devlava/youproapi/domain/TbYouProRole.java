package devlava.stmsapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "TB_STMS_ROLE")
public class TbStmsRole {

    @Id
    @Column(name = "skid")
    private String skid;

    @Column(name = "role")
    private String role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skid", referencedColumnName = "skid")
    private TbLmsMember member;
}
