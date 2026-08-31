package site.yesaido.ai_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.client.cultivation.CultivationMemberListResponse;
import site.yesaido.ai_server.dto.client.cultivation.HarvestDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
import site.yesaido.ai_server.dto.client.sensor.snapshot.DataGeneratorSnapshotResponse;
import site.yesaido.ai_server.dto.client.sensor.trend.SensorTrendPointListResponse;
import site.yesaido.ai_server.dto.cultivation.*;

import java.time.LocalDate;
import java.util.List;

@FeignClient(
        name = "cultivation-server",
        url = "${feign.client.cultivation-server.url}",
        path = "/api/v1")
public interface CultivationClient {
    @GetMapping("/cultivations/{cultivation-id}") // 재배 기본 정보(버섯 id)
    CultivationDetailResponse getCultivation(@RequestHeader("X-User-Id") Long userId,
                                             @PathVariable("cultivation-id") Long cultivationId);

    @GetMapping("/cultivations/users/{user-id}/ids") // 인사이트 조회할 때 내 재배 안뜨게 하게 위해 추가
    List<Long> getUserCultivationIds(@PathVariable("user-id") Long userId);

    @GetMapping("/cultivations/{cultivation-id}/harvest") // 수확 정보 조회
    HarvestDetailResponse getHarvest(@PathVariable("cultivation-id") Long cultivationId,
                                     @RequestHeader("X-User-Id") Long userId);

    @GetMapping("/cultivations/{cultivation-id}/sensors") // Cultivation에 어떤 센서 달아놨나 조회
    CultivationSensorListResponse getAllCultivationSensor(@RequestHeader("X-User-Id") Long userId,
                                                          @PathVariable("cultivation-id") Long cultivationId);

    // 환경 유지율 평균 조회
    @GetMapping("/cultivations/{cultivation-id}/environment-compliance")
    EnvironmentComplianceResponse getEnvironmentCompliance(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestHeader("X-User-Id") Long userId
    );

    /**
     * Asia/Seoul 달력 날짜를 기준으로 하루의 환경 유지율을 조회합니다.
     *
     * <p>Cultivation Server는 해당 날짜의 전체 원시 센서 측정 수 대비
     * 설정된 임계값 범위 안에 들어온 측정 수의 비율을 계산합니다.
     * 요청 시점 기준 rolling 24시간을 조회하는 Trend API와 달리
     * 이 API는 명시적으로 전달한 달력 날짜를 사용합니다.</p>
     *
     * <p>응답 필드가 null이면 0%가 아니라 해당 센서 타입의 설정이나
     * 측정 데이터가 없어 유지율을 계산할 수 없다는 의미입니다.
     * AI Client는 null을 0이나 다른 기본값으로 변환하지 않습니다.</p>
     *
     * <p>현재 응답은 온도, 습도, 이산화탄소, 조도의 네 가지 기본
     * 센서 유지율만 제공합니다. 커스텀 센서의 유지율은 이 API가
     * 제공하지 않으므로 별도의 참고 계산이 필요합니다.</p>
     */
    @GetMapping("/cultivations/{cultivation-id}/environment-compliance/daily")
    EnvironmentComplianceResponse getDailyEnvironmentCompliance(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestHeader("X-User-Id") Long userId
    );

    // 센서 평균값 조회
    @GetMapping("/cultivations/{cultivation-id}/sensor-values/average")
    SensorTypeAverageListResponse getSensorValuesAverage(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestHeader("X-User-Id") Long userId
    );

    // 대상 날짜에 사진이 등록된 활성 경작지의 사진 목록 조회
    @GetMapping("/internal/cultivations/photos/daily")
    DailyCultivationPhotoListResponse getDailyCultivationPhotos(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate targetDate
    );

    // cultivation DB의 전체 버섯 기준 정보 및 등록된 센서 임계값 조회
    @GetMapping("/api/v1/mushroom-references")
    MushroomReferenceInfoListResponse getMushroomReference();

    // Feign path /api/v1과 method path가 결합되어
    // /api/v1/internal/data-generator/snapshot을 호출한다.
    @GetMapping("/internal/data-generator/snapshot")
    DataGeneratorSnapshotResponse getDataGeneratorSnapshot();

    @GetMapping("/cultivations/{cultivation-id}/members")
    CultivationMemberListResponse getCultivationMembers(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestHeader("X-User-Id") Long requesterUserId,
            @RequestHeader("X-User-Role") String requesterRole
    );

    /**
     * 요청 시점을 기준으로 최근 24시간의 센서 추이를 조회합니다.
     *
     * <p>각 응답 측정점은 원시 데이터를 15분 구간별 평균으로 집계한
     * 값입니다. 특정 달력 날짜를 정확히 조회하는 API가 아니라
     * 계속 이동하는 최근 24시간 범위를 조회하는 계약입니다.</p>
     *
     * <p>일일 피드백에서는 이러한 조회 범위의 제약을
     * {@code contextSnapshot}에 명시적으로 남겨야 합니다.</p>
     */
    @GetMapping("/cultivations/{cultivation-id}/sensor-values/trend")
    SensorTrendPointListResponse getSensorTrend(
            @PathVariable("cultivation-id") Long cultivationId,
            @RequestParam("device-eui") String deviceEui,
            @RequestParam("sensor-type") String sensorType,
            @RequestHeader("X-User-Id") Long userId
    );
}
