package site.yesaido.ai_server.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnvironmentTool {
    private final CultivationClient cultivationClient;
    // 전체 재배지 목록 조회
    @Tool(description = "사용자가 현재 관리/운영 중인 전체 재배지 목록(재배지 ID, 버섯 종류, 재배 상태/모드 등)을 조회합니다.")
    public String getUserCultivations(
            @ToolParam(description = "사용자 고유 ID (숫자)") Long userId) {

        log.info("🔍 [Tool 호출] 사용자 ID {}의 재배지 목록 조회 시작", userId);
        try {
            List<Long> cultIds = cultivationClient.getUserCultivationIds(userId);
            if (cultIds == null || cultIds.isEmpty()) {
                return "현재 등록되어 운영 중인 재배지가 없습니다.";
            }

            StringBuilder sb = new StringBuilder("[현재 운영 중인 재배지 목록]\n");
            for (Long id : cultIds) {
                CultivationDetailResponse detail = cultivationClient.getCultivation(userId, id);
                if (detail != null) {
                    sb.append(String.format("- [재배지 ID: %d] 버섯 ID: %d | 상태: %s | 모드: %s%n",
                            detail.cultivationId(), detail.mushroomId(), detail.status(), detail.mode()));
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("재배지 목록 조회 중 오류 발생: {}", e.getMessage());
            return "재배지 목록을 불러오는 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // 특정 재배지의 실시간 센서 평균값 및 환경 적정 유지율 조회
    @Tool(description = "특정 재배지의 실시간 센서 평균값(온도, 습도, CO2, 조도)과 적정 환경 유지율(%)을 조회하여 농장 환경 상태를 점검합니다.")
    public String getCultivationEnvironmentStatus(
            @ToolParam(description = "사용자 고유 ID (숫자)") Long userId,
            @ToolParam(description = "조회할 재배지 ID (숫자)") Long cultivationId) {
        log.info("🔍 [Tool 호출] 재배지 ID {} 실시간 센서 및 환경 유지율 조회 시작 (사용자 ID: {})", cultivationId, userId);
        try {
            CultivationDetailResponse cult = cultivationClient.getCultivation(userId, cultivationId);
            SensorTypeAverageListResponse avgSensors = cultivationClient.getSensorValuesAverage(cultivationId, userId);
            EnvironmentComplianceResponse compliance = cultivationClient.getEnvironmentCompliance(cultivationId, userId);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[재배지 기본 정보]%n- 재배지 ID: %d | 버섯 ID: %d | 모드: %s%n%n",
                    cult.cultivationId(), cult.mushroomId(), cult.mode()));

            sb.append("[실시간 센서 평균값]\n");
            if (avgSensors != null && avgSensors.sensorTypeAverages() != null && !avgSensors.sensorTypeAverages().isEmpty()) {
                String sensorDetails = avgSensors.sensorTypeAverages().stream()
                        .map(s -> String.format("- %s: %.1f %s", s.sensorType(), s.averageValue(), s.unit()))
                        .collect(Collectors.joining("\n"));
                sb.append(sensorDetails).append("\n\n");
            } else {
                sb.append("- 측정된 센서 데이터가 아직 없습니다.\n\n");
            }

            sb.append("[환경 적정 유지율]\n");
            if (compliance != null) {
                sb.append(String.format("- 온도 유지율: %s%%%n", compliance.temperatureCompliance() != null ? compliance.temperatureCompliance().toPlainString() : "0.0"));
                sb.append(String.format("- 습도 유지율: %s%%%n", compliance.humidityCompliance() != null ? compliance.humidityCompliance().toPlainString() : "0.0"));
                sb.append(String.format("- CO2 유지율: %s%%%n", compliance.co2Compliance() != null ? compliance.co2Compliance().toPlainString() : "0.0"));
                sb.append(String.format("- 조도 유지율: %s%%%n", compliance.lightCompliance() != null ? compliance.lightCompliance().toPlainString() : "0.0"));
            } else {
                sb.append("- 유지율 데이터를 계산 중입니다.\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("재배지 환경 데이터 조회 중 오류 발생: {}", e.getMessage());
            return "재배지 환경 데이터를 조회하는 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
