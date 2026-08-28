package site.yesaido.ai_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import site.yesaido.ai_server.dto.client.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.client.cultivation.HarvestDetailResponse;
import site.yesaido.ai_server.dto.client.mushroom_reference.MushroomReferenceInfoListResponse;
import site.yesaido.ai_server.dto.client.sensor.CultivationSensorListResponse;
import site.yesaido.ai_server.dto.client.sensor.EnvironmentComplianceResponse;
import site.yesaido.ai_server.dto.client.sensor.SensorTypeAverageListResponse;
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
}