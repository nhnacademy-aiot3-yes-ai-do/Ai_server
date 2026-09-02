package site.yesaido.ai_server.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.context.UserContextHolder;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationSummaryListResponse;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnvironmentTool {
    private final CultivationClient cultivationClient;
    // 전체 재배지 목록 조회 (Gemini가 시스템 프롬프트의 userId를 직접 바인딩하여 도구 실행 강제)
    @Tool(name = "getUserCultivations", description = "데이터베이스에서 사용자의 전체 재배지 목록(재배지 ID, 이름, 버섯 품종, 재배 상태)을 실시간 조회하는 필수 도구입니다. 사용자가 '재배지 목록', '경작지 목록', '내 재배지', '농장 목록' 등을 물어보면 텍스트 답변 대신 반드시 이 함수를 호출해야 합니다.")
    public String getUserCultivations(@ToolParam(description = "조회할 사용자의 고유 ID (시스템 컨텍스트의 '현재 로그인된 사용자 ID' 값)", required = false) Long userId) {
        log.info("[EnvironmentTool.getUserCultivations 호출됨] 인자로 넘어온 userId: {}, UserContextHolder의 userId: {}", userId, UserContextHolder.getUserId());
        Long targetUserId = (userId != null) ? userId : UserContextHolder.getUserId();
        if (targetUserId == null) {
            log.error("UserContextHolder 및 ToolParam에 userId가 설정되어 있지 않습니다.");
            return "인증된 사용자 정보를 찾을 수 없습니다.";
        }

        log.info("사용자 ID {}의 재배지 목록 조회 시작", targetUserId);
        try {
            CultivationSummaryListResponse response = cultivationClient.getCultivations(targetUserId);
            log.info("CultivationClient.getCultivations({}) 조회 결과: {}", targetUserId, response);
            if (response == null || response.cultivationSummaryResponses() == null || response.cultivationSummaryResponses().isEmpty()) {
                return "현재 등록되어 운영 중인 재배지가 없습니다.";
            }

            StringBuilder sb = new StringBuilder("[현재 운영 중인 실제 재배지 목록]\n");
            for (site.yesaido.ai_server.dto.client.cultivation.CultivationSummaryResponse summary : response.cultivationSummaryResponses()) {
                sb.append(String.format("- [재배지 ID: %d] %s (버섯 ID: %d) (품종: %s) | 상태: %s | 모드: %s%n",
                        summary.cultivationId(), summary.name(), summary.mushroomId(), getMushroomNameById(summary.mushroomId()), summary.status(), summary.mode()));
            }
            return sb.toString();

        } catch (feign.FeignException e) {
            log.error("재배지 서버(Cultivation Server) 통신 장애 발생: status={}", e.status(), e);
            return "현재 재배지 서버와의 통신이 원활하지 않아 목록을 가져올 수 없습니다.";
        } catch (Exception e) {
            log.error("재배지 목록 조회 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return "일시적인 시스템 오류로 재배지 목록을 불러오지 못했습니다.";
        }
    }

    // 자바 내부 호출용 오버로딩 (기존 테스트 호환용)
    public String getUserCultivations() {
        return getUserCultivations(null);
    }

    // 버섯 ID를 직관적인 한글 이름으로 변환
    private String getMushroomNameById(Long mushroomId) {
        if (mushroomId == null) return "알 수 없음";
        return switch (mushroomId.intValue()) {
            case 1 -> "느타리버섯";
            case 2 -> "양송이버섯";
            case 3 -> "새송이버섯";
            case 4 -> "팽이버섯";
            case 5 -> "표고버섯";
            default -> "버섯 (ID:" + mushroomId + ")";
        };
    }

    // 특정 재배지의 실시간 센서 평균값 및 환경 적정 유지율 조회
    @Tool(description = "특정 재배지의 실시간 센서 평균값(온도, 습도, CO2, 조도)과 적정 환경 유지율(%)을 조회하여 농장 환경 상태를 점검합니다.")
    // LLM은 cultivationId만 넘기고 userId는 서버 컨텍스트에서 직접 꺼내는 방식으로 하여 보안 강화
    public String getCultivationEnvironmentStatus(@ToolParam(description = "조회할 재배지 ID (숫자)") Long cultivationId) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            log.error("UserContextHolder에 userId가 설정되어 있지 않습니다.");
            return "인증된 사용자 정보를 찾을 수 없습니다.";
        }
        log.info("재배지 ID {} 실시간 센서 및 환경 유지율 조회 시작 (사용자 ID: {})", cultivationId, userId);
        try {
            CultivationDetailResponse cult = cultivationClient.getCultivation(userId, cultivationId);
            if (cult == null) {
                return String.format("ID %d번에 해당하는 재배지 정보를 찾을 수 없습니다.", cultivationId);
            }
            SensorTypeAverageListResponse avgSensors = cultivationClient.getSensorValuesAverage(cultivationId, userId);
            EnvironmentComplianceResponse compliance = cultivationClient.getEnvironmentCompliance(cultivationId, userId);

            return """                                                                                                                                                                                                                                           
            [재배지 기본 정보]
            - 재배지 ID: %d | 버섯 ID: %d | 모드: %s
            
            [실시간 센서 평균값]
            %s
            [환경 적정 유지율]
            %s
            """.formatted(
                    cult.cultivationId(), cult.mushroomId(), cult.mode(),
                    formatSensorAverages(avgSensors),
                    formatCompliance(compliance)
            );

        } catch (feign.FeignException.NotFound e) {
            log.warn("요청한 재배지 정보를 찾을 수 없음: {}", e.getMessage());
            return "해당 ID의 재배지 정보가 존재하지 않습니다.";
        } catch (feign.FeignException e) {
            log.error("재배지 서버(Cultivation Server) 통신 장애 발생: status={}", e.status(), e);
            return "현재 재배지 서버와의 통신이 원활하지 않아 실시간 환경 데이터를 가져올 수 없습니다.";
        } catch (Exception e) {
            log.error("재배지 환경 데이터 조회 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return "일시적인 시스템 오류로 환경 데이터를 조회하지 못했습니다.";
        }
    }

    private String formatSensorAverages(SensorTypeAverageListResponse avgSensors) {
        if (avgSensors == null || avgSensors.sensorTypeAverages() == null || avgSensors.sensorTypeAverages().isEmpty()) {
            return "- 측정된 센서 데이터가 아직 없습니다.\n\n";
        }
        return avgSensors.sensorTypeAverages().stream()
                .map(s -> String.format("- %s: %.1f %s", s.sensorType(), s.averageValue(), s.unit()))
                .collect(Collectors.joining("\n")) + "\n\n";
    }

    private String formatCompliance(EnvironmentComplianceResponse compliance) {
        if (compliance == null) {
            return "- 유지율 데이터를 계산 중입니다.\n";
        }
        return String.format("- 온도 유지율: %s%%%n", formatPercent(compliance.temperatureCompliance()))
                + String.format("- 습도 유지율: %s%%%n", formatPercent(compliance.humidityCompliance()))
                + String.format("- CO2 유지율: %s%%%n", formatPercent(compliance.co2Compliance()))
                + String.format("- 조도 유지율: %s%%%n", formatPercent(compliance.lightCompliance()));
    }

    private String formatPercent(BigDecimal value) {
        return value != null ? value.toPlainString() : "0.0";
    }
}
