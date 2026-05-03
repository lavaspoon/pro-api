package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CsSatisfactionExcludeLogResponse {

    private List<Entry> entries;

    @Getter
    @Builder
    public static class Entry {
        private Long id;
        private String skill;
        private String startAt;
        private String endAt;
        private String excludedBySkid;
        private int updatedRowCount;
        private String createdAt;
    }
}
