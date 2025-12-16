package kr.wisead.domain.survey.scheduler;

import kr.wisead.domain.survey.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 이벤트 스케줄러 Service
 * - 만료된 이벤트 상태 자동 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventSchedulerService {

    private final EventService eventService;

    /**
     * 만료된 이벤트 상태 업데이트
     * 매 15분마다 실행 (정각, 15분, 30분, 45분)
     */
    @Scheduled(cron = "0 0,15,30,45 * * * *")
    public void updateExpiredEvents() {
        try {
            log.info("============================================================================================");
            log.info("배치 작업 시작: 만료된 이벤트 상태 업데이트");
            int updatedCount = eventService.updateExpiredEventsStatus();
            log.info("배치 작업 완료: [{}]개의 이벤트 상태가 'F'로 업데이트되었습니다.", updatedCount);
            log.info("============================================================================================");
        } catch (Exception e) {
            log.error("배치 작업 중 오류 발생: ", e);
            log.error("============================================================================================");
        }
    }
}
