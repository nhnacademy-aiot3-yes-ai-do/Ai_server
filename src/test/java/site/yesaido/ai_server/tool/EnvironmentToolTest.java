package site.yesaido.ai_server.tool;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.ai_server.client.CultivationClient;
import site.yesaido.ai_server.context.UserContextHolder;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationSummaryListResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationSummaryResponse;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentToolTest {
    @Mock
    private CultivationClient cultivationClient;

    @InjectMocks
    private EnvironmentTool environmentTool;

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private FeignException createFeignException() {
        Request request = Request.create(Request.HttpMethod.GET, "/test", new HashMap<>(), Request.Body.empty(), new RequestTemplate());
        return new FeignException.FeignClientException(500, "Internal Error", request, null, null);
    }

    @Test
    @DisplayName("getUserCultivations - UserContextHolder에 userId 미설정 시 인증 오류 반환")
    void getUserCultivations_noUserId() {
        String result = environmentTool.getUserCultivations();
        assertThat(result).contains("인증된 사용자 정보를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("getUserCultivations - 등록된 재배지 없을 때 안내 문구 반환")
    void getUserCultivations_emptyList() {
        UserContextHolder.setUserId(22L);
        when(cultivationClient.getCultivations(22L)).thenReturn(new CultivationSummaryListResponse(Collections.emptyList()));

        String result = environmentTool.getUserCultivations();
        assertThat(result).contains("현재 등록되어 운영 중인 재배지가 없습니다.");
    }

    @Test
    @DisplayName("getUserCultivations - 재배지 목록 정상 포맷팅 반환")
    void getUserCultivations_success() {
        UserContextHolder.setUserId(22L);
        // CultivationSummaryResponse 실제 8개 인자 생성자
        CultivationSummaryResponse item = new CultivationSummaryResponse(
                7L, "양송이재배지", 2L, "PROCEEDING", "GROWTH", 1, "ownerUser", LocalDateTime.now()
        );
        when(cultivationClient.getCultivations(22L)).thenReturn(new CultivationSummaryListResponse(List.of(item)));

        String result = environmentTool.getUserCultivations();
        assertThat(result).contains("[재배지 ID: 7] 양송이재배지 (버섯 ID: 2)");
    }

    @Test
    @DisplayName("getUserCultivations - FeignException 통신 장애 예외 처리")
    void getUserCultivations_feignException() {
        UserContextHolder.setUserId(22L);
        when(cultivationClient.getCultivations(22L)).thenThrow(createFeignException());

        String result = environmentTool.getUserCultivations();
        assertThat(result).contains("재배지 서버와의 통신이 원활하지 않아");
    }

    @Test
    @DisplayName("getUserCultivations - 예상치 못한 일반 Exception 처리")
    void getUserCultivations_genericException() {
        UserContextHolder.setUserId(22L);
        when(cultivationClient.getCultivations(22L)).thenThrow(new RuntimeException("DB down"));

        String result = environmentTool.getUserCultivations();
        assertThat(result).contains("일시적인 시스템 오류로 재배지 목록을 불러오지 못했습니다.");
    }

    @Test
    @DisplayName("getCultivationEnvironmentStatus - UserContextHolder에 userId 미설정 시 인증 오류")
    void getCultivationEnvironmentStatus_noUserId() {
        String result = environmentTool.getCultivationEnvironmentStatus(7L);
        assertThat(result).contains("인증된 사용자 정보를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("getCultivationEnvironmentStatus - 재배지가 null일 때 오류 문구 반환")
    void getCultivationEnvironmentStatus_cultivationNull() {
        UserContextHolder.setUserId(22L);
        when(cultivationClient.getCultivation(22L, 7L)).thenReturn(null);

        String result = environmentTool.getCultivationEnvironmentStatus(7L);
        assertThat(result).contains("ID 7번에 해당하는 재배지 정보를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("getCultivationEnvironmentStatus - 센서 평균값 및 유지율 정상 포맷팅 반환")
    void getCultivationEnvironmentStatus_success() {
        UserContextHolder.setUserId(22L);
        // CultivationDetailResponse 실제 5개 인자 생성자
        CultivationDetailResponse cult = new CultivationDetailResponse(
                7L, 2L, "PROCEEDING", "GROWTH", LocalDateTime.now()
        );
        // SensorTypeAverageResponse 실제 4개 인자 생성자 (cultivationId, sensorType, unit, averageValue)
        SensorTypeAverageListResponse avg = new SensorTypeAverageListResponse(List.of(
                new SensorTypeAverageResponse(7L, "TEMPERATURE", "°C", 16.5),
                new SensorTypeAverageResponse(7L, "HUMIDITY", "%", 80.0)
        ));
        // EnvironmentComplianceResponse 실제 4개 인자 생성자
        EnvironmentComplianceResponse compliance = new EnvironmentComplianceResponse(
                new BigDecimal("95.5"), new BigDecimal("88.0"), new BigDecimal("92.0"), new BigDecimal("100.0")
        );

        when(cultivationClient.getCultivation(22L, 7L)).thenReturn(cult);
        when(cultivationClient.getSensorValuesAverage(7L, 22L)).thenReturn(avg);
        when(cultivationClient.getEnvironmentCompliance(7L, 22L)).thenReturn(compliance);

        String result = environmentTool.getCultivationEnvironmentStatus(7L);
        assertThat(result).contains("TEMPERATURE: 16.5 °C", "HUMIDITY: 80.0 %", "온도 유지율: 95.5%");
    }

    @Test
    @DisplayName("getCultivationEnvironmentStatus - 센서 데이터 및 유지율이 null일 때 기본 안내 문구 검증")
    void getCultivationEnvironmentStatus_nullSensorsAndCompliance() {
        UserContextHolder.setUserId(22L);
        CultivationDetailResponse cult = new CultivationDetailResponse(
                7L, 2L, "PROCEEDING", "GROWTH", LocalDateTime.now()
        );

        when(cultivationClient.getCultivation(22L, 7L)).thenReturn(cult);
        when(cultivationClient.getSensorValuesAverage(7L, 22L)).thenReturn(null);
        when(cultivationClient.getEnvironmentCompliance(7L, 22L)).thenReturn(null);

        String result = environmentTool.getCultivationEnvironmentStatus(7L);
        assertThat(result).contains("측정된 센서 데이터가 아직 없습니다.", "유지율 데이터를 계산 중입니다.");
    }

    @Test
    @DisplayName("getCultivationEnvironmentStatus - FeignException 404 NotFound 처리")
    void getCultivationEnvironmentStatus_notFound() {
        UserContextHolder.setUserId(22L);
        Request request = Request.create(Request.HttpMethod.GET, "/test", new HashMap<>(), Request.Body.empty(), new RequestTemplate());
        when(cultivationClient.getCultivation(22L, 7L)).thenThrow(new FeignException.NotFound("Not found", request, null, null));

        String result = environmentTool.getCultivationEnvironmentStatus(7L);
        assertThat(result).contains("해당 ID의 재배지 정보가 존재하지 않습니다.");
    }
}
