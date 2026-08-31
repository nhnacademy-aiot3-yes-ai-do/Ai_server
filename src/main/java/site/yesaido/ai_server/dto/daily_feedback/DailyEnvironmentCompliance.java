package site.yesaido.ai_server.dto.daily_feedback;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 특정 경작지의 명시적인 달력 날짜에 대한 공식 환경 유지율입니다.
 *
 * <p>날짜는 Cultivation Server가 Asia/Seoul 기준으로 해석합니다.
 * Cultivation Server는 해당 날짜의 전체 원시 센서 측정 수 대비
 * 현재 설정된 임계값 범위 안에 들어온 측정 수의 비율로 유지율을
 * 계산합니다. 각 유지율의 단위는 0부터 100까지의 퍼센트입니다.</p>
 *
 * <p>이 값은 요청 시점을 기준으로 조회하는 rolling 24시간 센서 통계와
 * 시간 기준이 다르며, 명시적으로 요청한 달력 날짜 하루를 대상으로 합니다.</p>
 *
 * <p>현재 공식 응답은 온도, 습도, 이산화탄소, 조도의 네 가지 기본
 * 센서 타입만 제공합니다. 각 값은 EUI별로 구분한 통계가 아니라
 * Cultivation Server가 경작지와 센서 타입을 기준으로 집계한
 * 공식 유지율입니다.</p>
 *
 * <p>커스텀 센서의 참고 유지율은 별도로 계산해야 하며 이 DTO에
 * 포함하지 않습니다.</p>
 *
 * <p>유지율이 null이면 0%가 아니라 해당 센서 타입의 설정 또는
 * 해당 날짜의 측정 데이터가 없어 계산할 수 없다는 의미입니다.
 * 네 유지율이 모두 null인 경우도 정상적으로 표현할 수 있습니다.</p>
 *
 * @param cultivationId 유지율을 조회한 경작지 ID
 * @param date Asia/Seoul 기준 유지율 대상 달력 날짜
 * @param temperatureCompliance 온도 유지율 또는 계산할 수 없는 경우 null
 * @param humidityCompliance 습도 유지율 또는 계산할 수 없는 경우 null
 * @param co2Compliance 이산화탄소 유지율 또는 계산할 수 없는 경우 null
 * @param lightCompliance 조도 유지율 또는 계산할 수 없는 경우 null
 */
public record DailyEnvironmentCompliance(
        Long cultivationId,
        LocalDate date,
        BigDecimal temperatureCompliance,
        BigDecimal humidityCompliance,
        BigDecimal co2Compliance,
        BigDecimal lightCompliance
) {

    public DailyEnvironmentCompliance {
        if (cultivationId == null || cultivationId <= 0) {
            throw new IllegalArgumentException("cultivationId는 null이 아니며 0보다 커야 합니다.");
        }

        if (date == null) {
            throw new IllegalArgumentException("date는 null일 수 없습니다.");
        }

        validatePercentage("temperatureCompliance", temperatureCompliance);
        validatePercentage("humidityCompliance", humidityCompliance);
        validatePercentage("co2Compliance", co2Compliance);
        validatePercentage("lightCompliance", lightCompliance);
    }

    private static void validatePercentage(String fieldName, BigDecimal value) {
        if (value == null) {
            return;
        }

        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("%s는 0 이상 100 이하이어야 합니다: value=%s"
                            .formatted(fieldName, value)
            );
        }
    }
}
