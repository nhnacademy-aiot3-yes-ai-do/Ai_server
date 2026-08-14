//package site.yesaido.ai_server.service;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.Resource;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.stereotype.Service;
//import site.yesaido.ai_server.client.CultivationClient;
//import site.yesaido.ai_server.dto.front.AiSensorResultDto;
//import site.yesaido.ai_server.dto.front.SensorRecommendationRequest;
//
//@Service
//@RequiredArgsConstructor
//public class SensorRecommendationService {
//    private final ChatClient chatClient;
//    private final CultivationClient cultivationClient;
//
//    /**
//     * 센서 임계값 AI 추천 결과를 캐싱하기 위한 RedisTemplate
//     * - 이유: 동일한 버섯/센서에 대한 AI API 중복 호출을 막아 비용과 응답 시간을 획기적으로 줄임
//     * - 구조: Redis Hash를 활용하여 [버섯 ID(Key) -> 센서 ID(HashKey) -> AI결과(Value)] 구조로 미지의 센서까지 동적 저장
//     */
//    private final RedisTemplate<String, Object> redisTemplate;
//
//    @Value("classpath:prompts/sensor_recommendation_system.st")
//    private Resource systemResource;
//
//    @Value("classpath:prompts/sensor_recommendation_user.st")
//    private Resource userResource;
//
//    public AiSensorResultDto getRecommendations(Long userId, SensorRecommendationRequest request) {
//        // OpenFeign으로 재배지 상세 정보 및 센서 목록 조회
//        var cultivation = cultivationClient.getCultivation(userId, request.cultivationId());
//        var sensors = cultivationClient
//
//
//
//        return null;
//    }
//}
