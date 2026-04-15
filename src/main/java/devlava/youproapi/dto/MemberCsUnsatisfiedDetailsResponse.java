package devlava.youproapi.dto;

import java.util.List;

public record MemberCsUnsatisfiedDetailsResponse(
        List<MemberCsUnsatisfiedRecordItem> records
) {}
