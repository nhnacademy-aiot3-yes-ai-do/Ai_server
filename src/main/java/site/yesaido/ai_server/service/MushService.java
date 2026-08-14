package site.yesaido.ai_server.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import site.yesaido.ai_server.dto.ai.mush_summary.MushGuideResponse;
import site.yesaido.ai_server.dto.ai.mush_summary.MushroomCsvDto;
import site.yesaido.ai_server.exception.MushDataNotFoundException;
import site.yesaido.ai_server.reader.MushCsvReader;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired}) // 스프링에게 이 생성자로 의존성 주입하라고 명시
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class MushService{
    private final ChatClient chatClient;
    private final MushCsvReader mushCsvReader;
    // StringTemplate 변수가 들어가는 프롬프트 탬플릿 파일임을 명시하기 위해 .st. 사용
    @Value("classpath:prompts/mush_guide_system.st")
    private Resource systemPrompt;

    @Value("classpath:prompts/mush_guide_user.st")
    private Resource userPrompt;


    // 결과 Redis에 저장해 다음엔 AI 거치지 않고 꺼낼 수 있게 해줌
    // Spring AI의 PromptTemplate 기능 사용하는 방식으로 변경 <- 프롬프트를 안전하고 동적으로 관리할 수 있음
    @Cacheable(value = "ai:mushroom", key = "#mushroomId + ':guide'")
    public MushGuideResponse generateRealDataGuide(Long mushroomId) {
        if (chatClient == null || mushCsvReader == null) {
            log.warn("chatClient 또는 mushCsvReader가 null입니다");
            throw new IllegalStateException("chatClient 또는 mushCsvReader가 null입니다");
        }
        log.info("캐시가 만료되어 다시 {}번 데이터 요약을 시작합니다.", mushroomId);

        String mushroomName = "";
        StringBuilder combinedData = new StringBuilder();

        List<MushroomCsvDto> csvDtoList = mushCsvReader.readMushroomCsv();
        for (MushroomCsvDto dto : csvDtoList) {
            if(dto.mushroomId().equals(mushroomId)) {
                mushroomName = dto.mushroomName();
                combinedData.append("[").append(dto.title()).append("] ").append(dto.content()).append("\n");
            }
        }

        // 뽑아낸 데이터 없으면 에러 발생
        if(combinedData.isEmpty()){
            log.error("ID {}에 해당하는 버섯 학습 데이터가 없습니다.",mushroomId);
            throw new MushDataNotFoundException(mushroomId);
        }

        final String finalMushroomName = mushroomName;
        final String finalCombinedData = combinedData.toString();


        log.info("'{}' 가이드라인 및 최적 제어 환경 요약 구조화 출력(Gemini Flash) 요청 시작...", finalMushroomName);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userPrompt)
                        .param("mushroomId", mushroomId)
                        .param("mushroomName", finalMushroomName)
                        .param("combinedData", finalCombinedData))
                .call()
                .entity(MushGuideResponse.class);
    }
}
