package site.yesaido.ai_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
import site.yesaido.ai_server.dto.cultivation.HarvestDetailResponse;

import java.util.List;

@FeignClient(name = "cultivation-server")
public interface CultivationClient {
    @GetMapping("/api/cultivations/{cultivation-id}") // 재배 기본 정보(버섯 id)
    CultivationDetailResponse getCultivation(@PathVariable("cultivation-id") Long cultivationId);

    @GetMapping("/api/cultivations/users/{user-id}/ids") // 인사이트 조회할 때 내 재배 안뜨게 하게 위해 추가
    List<Long> getUserCultivationIds(@PathVariable("user-id") Long userId);

    @GetMapping("/api/cultivations/{cultivation-id}/harvest") // 수확 정보 조회
    HarvestDetailResponse getHarvest(@PathVariable("cultivation-id") Long cultivationId,
                                     @RequestHeader("X-User-Id") Long userId);

    // 전체 기간 센서 평균값 조회(Cultivation에 코드 수정중이라 보류) 숫자만 들어오는게 아니 온도면 20℃ 이렇게 들어옴?


    // 누적 환경 유지율 평균(Cultivation에 코드 수정중이라 보류)
    // 임계값이 넘으면 정상값 100 정상 90 10 환경유지율 90

}