package devlava.youproapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * YOU PRO 인센티브 반영 스케줄러.
 *
 * <p>매년 <strong>2~10월 매달 1일 18:00</strong>에 실행하여
 * <strong>전월(1~9월)</strong> 실적을 반영한다. 프로그램 기간은 매년 1~9월이다.
 *
 * <ul>
 *   <li>전월 부서 스킬 CS 만족도 목표 달성 시에만 해당 월 선정 건수가 누적(인증) 반영된다.</li>
 *   <li>만족도 달성했으나 선정 건이 0이면 인센티브 지급 없음, 등급(누적)만 유지된다.</li>
 *   <li>실행 시 해당 월 반영 대상 평가대상자 인원을 {@code tb_you_incentive_month_stat}에 기록한다.</li>
 * </ul>
 *
 * <pre>
 *  실행일(18시)   처리 대상
 *  ────────────   ────────
 *  2월 1일    →  1월
 *  3월 1일    →  2월
 *  ...
 *  10월 1일   →  9월
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncentiveReflectScheduler {

    private final IncentiveReflectService incentiveReflectService;

    /**
     * 매달 1일 18:00 실행 (2~10월만). 전월(1~9월) 반영.
     *
     * <p>cron: {@code 0 0 18 1 2-10 ?} — 초·분·시·일·월·요일(무관)
     */
    @Scheduled(cron = "0 0 18 1 2-10 ?")
    public void runMonthlyReflect() {
        // 1일 실행 시 전일 = 전월 말일 → 반영 대상 연·월
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
