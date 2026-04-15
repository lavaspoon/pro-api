package devlava.youproapi.dto;

import java.time.LocalDate;

/**
 * 구성원 불만족 상담 1건 상세 (TbCsSatisfactionRecord 기준, 화면 표시용).
 */
public record MemberCsUnsatisfiedRecordItem(
        Long id,
        LocalDate evalDate,
        String consultTime,
        String subsidiaryType,
        String centerName,
        String groupName,
        String roomName,
        String consultType1,
        String consultType2,
        String consultType3,
        String skill,
        String satisfiedYn,
        String goodMent,
        String badMent,
        String fiveMajorCitiesYn,
        String gen5060Yn,
        String problemResolvedYn
) {}
