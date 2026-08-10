//package site.yesaido.ai_server.client;
//
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import site.yesaido.ai_server.dto.cultivation.CultivationDetailResponse;
//
//@FeignClient(name = "cultivation-server")
//public interface CultivationClient {
//    @GetMapping("/api/cultivations/{cultivation-id}")
//    CultivationDetailResponse getCultivation(@PathVariable("cultivation-id") Long cultivationId);
//}