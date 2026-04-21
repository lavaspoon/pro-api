package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TargetMemberUploadResponse {
    private int inserted;
    private int updatedMembers;
    private int skipped;
    private List<String> warnings;
}
