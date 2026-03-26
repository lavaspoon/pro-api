package devlava.youproapi.service;

import devlava.youproapi.domain.TbYouStt;
import devlava.youproapi.dto.SttResultDto;
import devlava.youproapi.repository.TbYouSttRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 유선_유프로_STT 조회 — 접수 {@code call_date}(일자·상담시간)와 매칭.
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
        String normalized = normalizeCallTime(callDate);
        Optional<IlJaSangdam> keys = parseIlJaAndSangdamSiGan(normalized);
        if (keys.isEmpty()) {
            log.warn("[STT] 일자·상담시간 파싱 불가 | callDate='{}'", callDate);
            return SttResultDto.notFound(callDate);
        }
        IlJaSangdam k = keys.get();
        Optional<TbYouStt> exact = youSttRepository.findFirstByIlJaAndSangdamSiGan(k.ilja(), k.sangdamSiGan());
        if (exact.isPresent()) {
            log.debug("[STT] 일자·상담시간 조회 성공 | callDate='{}'", callDate);
            return SttResultDto.from(exact.get());
        }
        if (k.sangdamSiGan().length() >= 4) {
            String prefix = k.sangdamSiGan().substring(0, 4) + "%";
            Optional<TbYouStt> like = youSttRepository.findFirstByIlJaAndSangdamSiGanPrefix(k.ilja(), prefix);
            if (like.isPresent()) {
                return SttResultDto.from(like.get());
            }
        }
        log.warn("[STT] 일치 없음 | callDate='{}', agentSkid='{}'", callDate, agentSkid);
        return SttResultDto.notFound(callDate);
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
