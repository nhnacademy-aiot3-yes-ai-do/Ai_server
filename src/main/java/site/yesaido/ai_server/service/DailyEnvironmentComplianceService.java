package site.yesaido.ai_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.daily_feedback.DailyEnvironmentCompliance;

import java.time.LocalDate;

/**
 * 특정 경작지의 명시적인 날짜에 대한 공식 환경 유지율을 조회합니다.
 *
 * <p>이 서비스는 Cultivation Server에 Asia/Seoul 기준 달력 날짜를
 * 명시적으로 전달합니다. 요청 시점을 기준으로 이동하는 rolling 24시간
 * 센서 통계와 달리 정확히 요청한 달력 날짜 하루를 조회합니다.</p>
 *
 * <p>OWNER 사용자 ID는 Cultivation Server의 현재 사용자 권한 계약을
 * 만족시키기 위해 상위 일일 피드백 조립 계층에서 한 번 조회한 뒤
 * 이 서비스에 전달합니다. 이 서비스는 OWNER를 다시 조회하지 않습니다.</p>
 *
 * <p>Cultivation Server의 응답 DTO에는 경작지 ID와 날짜가 포함되지
 * 않으므로 요청에 사용한 {@code cultivationId}와 {@code date}를
 * 공식 유지율 값과 함께 보존합니다.</p>
 *
 * <p>null 응답이나 0~100 범위를 위반한 응답은 데이터 없음으로 숨기지
 * 않고 외부 응답 계약 오류로 전파합니다. 반면 개별 유지율의 null은
 * 해당 센서 타입의 설정 또는 측정 데이터가 없어 계산할 수 없는
 * 정상 상태이므로 그대로 유지합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyEnvironmentComplianceService {

    private final CultivationClient cultivationClient;

    /**
     * 지정한 경작지와 날짜의 공식 환경 유지율을 조회합니다.
     *
     * @param cultivationId 유지율을 조회할 경작지 ID
     * @param date Asia/Seoul 기준 조회 대상 달력 날짜
     * @param ownerUserId Cultivation 사용자 권한 검사에 사용할 OWNER 사용자 ID
     * @return 요청 식별정보와 공식 환경 유지율을 함께 보존한 결과
     * @throws IllegalArgumentException 입력값이 유효하지 않은 경우
     * @throws IllegalStateException 응답이 null이거나 유지율 범위를 위반한 경우
     */
    public DailyEnvironmentCompliance fetch(Long cultivationId, LocalDate date, Long ownerUserId) {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (date == null) {
            throw new IllegalArgumentException("date는 null일 수 없습니다.");
        }

        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId는 null이 아니며 0보다 커야 합니다.");
        }

        EnvironmentComplianceResponse response = cultivationClient
                .getDailyEnvironmentCompliance(cultivationId, date, ownerUserId);

        if (response == null) {
            throw new IllegalStateException("날짜별 환경 유지율 응답이 null입니다:cultivationId=%s, date=%s "
                    .formatted(cultivationId, date).strip());
        }

        try {
            return new DailyEnvironmentCompliance(
                    cultivationId,
                    date,
                    response.temperatureCompliance(),
                    response.humidityCompliance(),
                    response.co2Compliance(),
                    response.lightCompliance()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("날짜별 환경 유지율 응답 계약이 유효하지 않습니다:cultivationId=%s, date=%s "
                    .formatted(cultivationId, date).strip(), exception);
        }
    }
}
