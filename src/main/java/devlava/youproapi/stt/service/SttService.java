package devlava.youproapi.stt.service;

import devlava.youproapi.stt.domain.TbSttResult;
import devlava.youproapi.stt.dto.SttResultDto;
import devlava.youproapi.stt.repository.TbSttResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * STT 조회 서비스
 *
 * <p>구성원이 우수사례를 접수할 때 입력한 <b>통화 시간(callTime)</b>과
 * <b>상담사 ID(agentSkid)</b>를 기준으로 {@code st_etc.TB_STT_RESULT} 를 조회한다.
 *
 * <h3>3단계 폴백 조회 전략</h3>
 * <ol>
 *   <li><b>정확 일치</b> — 정규화된 callTime + agentSkid 완전 일치</li>
 *   <li><b>분 단위 LIKE</b> — callTime 앞 12자리로 LIKE 검색 (초 오차 허용)</li>
 *   <li><b>found=false 반환</b> — 매칭 실패 시 빈 결과 반환 (프론트에서 수동 입력 유도)</li>
 * </ol>
 *
 * <h3>callTime VARCHAR 정규화</h3>
 * <p>STT 시스템의 저장 포맷에 무관하게 숫자만 추출하여 비교한다.
 * <pre>
 *   "2026-03-05 09:30:00" → "20260305093000"
 *   "20260305 09:30"      → "202603050930"
 *   "20260305093000"      → "20260305093000"
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttService {

    private final TbSttResultRepository sttResultRepository;

    /**
     * 통화시간 + 상담사 skid 로 STT 결과 조회.
     *
     * @param callTime  사용자가 입력한 통화 시각 (원본 문자열)
     * @param agentSkid 상담사 skid
     * @return STT 결과 DTO ({@code found=false} 이면 데이터 없음)
     */
    @Transactional(transactionManager = "sttTransactionManager", readOnly = true)
    public SttResultDto findByCallTimeAndAgent(String callTime, String agentSkid) {

        String normalized = normalizeCallTime(callTime);
        log.debug("[STT] 조회 시작 | callTime='{}' → normalized='{}', agentSkid='{}'",
                callTime, normalized, agentSkid);

        // ── 1단계: 정확 일치 ──────────────────────────────────────────────────
        Optional<TbSttResult> exact =
                sttResultRepository.findByCallTimeAndAgentSkid(normalized, agentSkid);
        if (exact.isPresent()) {
            log.debug("[STT] 정확 일치 성공 | sttId='{}'", exact.get().getSttId());
            return SttResultDto.from(exact.get());
        }

        // ── 2단계: 분 단위 LIKE (앞 12자리 = YYYYMMDDHHMI) ────────────────────
        if (normalized.length() >= 12) {
            String prefix = normalized.substring(0, 12) + "%";
            List<TbSttResult> likeResults =
                    sttResultRepository.findByCallTimeLikeAndAgentSkid(prefix, agentSkid);
            if (!likeResults.isEmpty()) {
                log.debug("[STT] LIKE 조회 성공 | prefix='{}', 건수={}", prefix, likeResults.size());
                return SttResultDto.from(likeResults.get(0));
            }
        }

        // ── 3단계: 매칭 실패 ──────────────────────────────────────────────────
        log.warn("[STT] 일치하는 STT 없음 | callTime='{}', agentSkid='{}'", callTime, agentSkid);
        return SttResultDto.notFound(callTime);
    }

    /**
     * 사례 접수 정보로 STT 결과 조회.
     *
     * <p>날짜만(8자리)이면 해당 일의 통화를 LIKE 검색하고, 일시(분 단위까지)면 더 좁게 매칭한다.
     *
     * @param callDate  접수 시 저장한 통화 일시 (예: {@code "2026-03-05 09:30:00"})
     * @param agentSkid 상담사 skid
     * @return STT 결과 DTO
     */
    @Transactional(transactionManager = "sttTransactionManager", readOnly = true)
    public SttResultDto findByCaseInfo(String callDate, String agentSkid) {
        if (callDate == null || callDate.isBlank() || agentSkid == null || agentSkid.isBlank()) {
            return SttResultDto.notFound(callDate);
        }

        String normalized = normalizeCallTime(callDate);
        // 날짜만 있는 경우(8자리) → 해당 날짜의 모든 통화 LIKE 검색
        String prefix = normalized + "%";
        List<TbSttResult> results =
                sttResultRepository.findByCallTimeLikeAndAgentSkid(prefix, agentSkid);

        if (!results.isEmpty()) {
            log.debug("[STT] 날짜 기반 조회 성공 | date='{}', 건수={}", callDate, results.size());
            return SttResultDto.from(results.get(0));
        }

        log.warn("[STT] 날짜 기반 일치 없음 | callDate='{}', agentSkid='{}'", callDate, agentSkid);
        return SttResultDto.notFound(callDate);
    }

    /**
     * 상담사의 최근 STT 통화 목록 조회 (수동 선택 UI 제공용).
     *
     * <p>자동 매칭 실패 시 프론트엔드에서 드롭다운으로 통화를 직접 선택할 수 있도록
     * 최근 10건을 제공한다.
     *
     * @param agentSkid 상담사 skid
     * @return 최근 STT 결과 목록 (최대 10건)
     */
    @Transactional(transactionManager = "sttTransactionManager", readOnly = true)
    public List<SttResultDto> findRecentByAgent(String agentSkid) {
        log.debug("[STT] 최근 통화 목록 조회 | agentSkid='{}'", agentSkid);
        return sttResultRepository
                .findTop10ByAgentSkidOrderByCallTimeDesc(agentSkid)
                .stream()
                .map(SttResultDto::from)
                .toList();
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    /**
     * callTime 문자열에서 숫자만 추출해 정규화.
     *
     * <p>STT 테이블의 VARCHAR 포맷이 어떠하든 동일 비교 가능하도록 변환.
     *
     * @param callTime 원본 통화시각 문자열
     * @return 숫자만으로 구성된 정규화 문자열
     */
    public String normalizeCallTime(String callTime) {
        if (callTime == null || callTime.isBlank()) return "";
        return callTime.replaceAll("[^0-9]", "");
    }
}
