package devlava.youproapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.Instant;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tb_cs_dissatisfaction_type")
public class TbCsDissatisfactionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "type_code", nullable = false, length = 64)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 200)
    private String typeName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
