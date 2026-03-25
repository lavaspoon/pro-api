package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "TB_YOUPRO_ROLE")
public class TbYouProRole {

    @Id
    @Column(name = "skid")
    private String skid;

    @Column(name = "role")
    private String role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skid", referencedColumnName = "skid")
    private TbLmsMember member;
}
