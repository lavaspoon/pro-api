package devlava.youproapi.service;

import devlava.youproapi.domain.TbYouProStt;
import devlava.youproapi.dto.SttResultDto;
import devlava.youproapi.repository.TbYouSttRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@code TB_YOU_PRO_STT} 조회 — 접수 {@code skid}, {@code call_date}(일자·상담시간)와 매칭.
 * <p>DB 종류와 무관하게 {@code reg_date} 비교는 애플리케이션에서 숫자만 추출해 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final TbYouSttRepository youSttRepository;

    @Transactional(readOnly = true)
    public SttResultDto findByCaseInfo(String callDate, String agentSkid) {
        if (callDate == null || callDate.isBlank()) {
            return SttResultDto.notFound(callDate);
        }
        if (agentSkid == null || agentSkid.isBlank()) {
            log.warn("[STT] skid 없음 | callDate='{}'", callDate);
            return SttResultDto.notFound(callDate);
        }
        String normalized = normalizeCallTime(callDate);
        Optional<IlJaSangdam> keys = parseIlJaAndSangdamSiGan(normalized);
        if (keys.isEmpty()) {
            log.warn("[STT] 일자·상담시간 파싱 불가 | callDate='{}'", callDate);
            return SttResultDto.notFound(callDate);
        }
        IlJaSangdam k = keys.get();
        final List<TbYouProStt> rows;
        try {
            rows = youSttRepository.findBySkidOrderBySttIdAsc(agentSkid.trim());
        } catch (DataAccessException e) {
            log.warn(
                    "[STT] TB_YOU_PRO_STT 조회 실패 — 테이블 미생성·권한·스키마 문제일 수 있음. STT 없이 진행합니다. cause={}",
                    e.getMostSpecificCause().getMessage());
            return SttResultDto.notFound(callDate);
        }

        Optional<TbYouProStt> exact = pickExact(rows, k.ilja(), k.sangdamSiGan());
        if (exact.isPresent()) {
            log.debug("[STT] skid·일자·상담시간 조회 성공 | callDate='{}'", callDate);
            return SttResultDto.from(exact.get());
        }
        if (k.sangdamSiGan().length() >= 4) {
            Optional<TbYouProStt> like = pickPrefix(rows, k.ilja(), k.sangdamSiGan());
            if (like.isPresent()) {
                return SttResultDto.from(like.get());
            }
        }
        log.warn("[STT] 일치 없음 | callDate='{}', agentSkid='{}'", callDate, agentSkid);
        return SttResultDto.notFound(callDate);
    }

    /** {@code reg_date} 에서 숫자만 남긴 문자열이 {@code 일자+상담시간} 과 같을 때까지 {@code stt_id} 순으로 탐색. */
    private static Optional<TbYouProStt> pickExact(List<TbYouProStt> rows, String ilja, String sangdamSiGan) {
        String target = ilja + sangdamSiGan;
        for (TbYouProStt t : rows) {
            if (digitsOnly(t.getRegDate()).equals(target)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** {@code reg_date} 숫자열이 {@code 일자 + 상담시간 앞 4자리} 로 시작하는 첫 행 (기존 LIKE 접두사 매칭). */
    private static Optional<TbYouProStt> pickPrefix(List<TbYouProStt> rows, String ilja, String sangdamSiGan) {
        String head = ilja + sangdamSiGan.substring(0, 4);
        for (TbYouProStt t : rows) {
            String reg = digitsOnly(t.getRegDate());
            if (reg.startsWith(head)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    private static String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^0-9]", "");
    }

    public String normalizeCallTime(String callTime) {
        if (callTime == null || callTime.isBlank()) {
            return "";
        }
        return callTime.replaceAll("[^0-9]", "");
    }

    public Optional<IlJaSangdam> parseIlJaAndSangdamSiGan(String normalizedDigits) {
        if (normalizedDigits == null || normalizedDigits.length() < 12) {
            return Optional.empty();
        }
        String ilja = normalizedDigits.substring(0, 8);
        String sangdam = normalizedDigits.substring(8, 12);
        return Optional.of(new IlJaSangdam(ilja, sangdam));
    }

    public record IlJaSangdam(String ilja, String sangdamSiGan) {}
}
