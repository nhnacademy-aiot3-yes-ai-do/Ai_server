package site.yesaido.ai_server.dto.ai.insight;

public record DailyStatsSummary( // 집계 결과용 DTO(모드 전환일, 알림 통계, 병충해 유무, 안정 일수 등 분석된 지표  묶어주는 dto)
        String modeSwitchInfo,        // 생육기에서 수확기로 모드가 전환된 시점 정보
        int totalEvents,              // 재배 기간 동안 발생한 전체 이벤트 총합
        int thresholdAlerts,          // 적정 임계값을 벗어난 경고 알림 발생 횟수
        int actuatorSuccessCount,     // 액추에이터 환경 제어 성공 횟수
        String diseaseStatusText,     // Vision AI 사진 분석 기반 병충해 감지 여부
        String stableDaysText,        // 임계값 이탈 없이 환경이 안정 유지된 일수 요약
        String dailySummaryExcerpt    // 시작일/중간일/수확일 대표 일일 피드백 발췌본
) {
}
