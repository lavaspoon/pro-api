package devlava.youproapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class CsSatisfactionUploadResponse {

    private int inserted;
    private int skipped;
    private List<LocalDate> replacedDates;
    private List<String> warnings;
}
