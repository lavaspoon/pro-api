package devlava.youproapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * YOU PRO 인센티브 반영 스케줄러.
 *
 * <p>매년 2월 1일 ~ 10월 1일(매달 1일 자정)에 실행하여
 * <strong>전달(1~9월)</strong>의 CS 만족도 달성 여부를 확인하고
 * 선정건수를 실적에 반영한다.
 *
 * <pre>
 *  실행일       처리 대상
 *  ──────────   ────────
 *  2월 1일  →  1월
 *  3월 1일  →  2월
 *  ...
 *  10월 1일 →  9월
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncentiveReflectScheduler {

    private final IncentiveReflectService incentiveReflectService;

    /**
     * 매달 1일 자정 실행 (2~10월만).
     *
     * <p>cron 표현식: {@code 0 0 0 1 2-10 *}
     * <ul>
     *   <li>초=0, 분=0, 시=0 → 자정</li>
     *   <li>일=1            → 1일</li>
     *   <li>월=2-10         → 2월~10월만</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 0 1 2-10 *")
    public void runMonthlyReflect() {
        // 실행 당일(1일)에서 하루 빼면 전달 마지막 날 → 연·월 추출
        LocalDate prevMonthDay = LocalDate.now().minusDays(1);
        int year  = prevMonthDay.getYear();
        int month = prevMonthDay.getMonthValue();

        log.info("[IncentiveReflect] 스케줄 실행 — 처리 대상: {}년 {}월", year, month);
        try {
            incentiveReflectService.processMonth(year, month);
        } catch (Exception e) {
            log.error("[IncentiveReflect] 스케줄 처리 중 예외 — {}년 {}월: {}",
                    year, month, e.getMessage(), e);
        }
    }
}
