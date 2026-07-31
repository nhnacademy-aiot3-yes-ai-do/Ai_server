package site.yesaido.ai_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DtoTest {
    @Test
    @DisplayName("MushroomCsvDto 객체 생성 및 값 일치 확인")
    void MushroomCsvDtoTest(){
        MushroomCsvDto mushroomCsvDto = new MushroomCsvDto(1L, "느타리버섯",
                "느타리버섯의 외형적 특징", "느타리버섯의 갓은...");
        assertThat(mushroomCsvDto.mushroomId()).isEqualTo(1L);
        assertThat(mushroomCsvDto.mushroomName()).isEqualTo("느타리버섯");
        assertThat(mushroomCsvDto.title()).isEqualTo("느타리버섯의 외형적 특징");
        assertThat(mushroomCsvDto.content()).isEqualTo("느타리버섯의 갓은...");
    }

    @Test
    @DisplayName("AiEvaluationDto 객체 생성 및 값 일치 확인")
    void aiEvaluationDtoTest(){
        AiEvaluationDto aiEvaluationDto = new AiEvaluationDto(2, 4,
                "건조함에 매우 취약", "~~");
        assertThat(aiEvaluationDto.difficultyLevel()).isEqualTo(2);
        assertThat(aiEvaluationDto.growthSpeed()).isEqualTo(4);
        assertThat(aiEvaluationDto.sensitivity()).isEqualTo("건조함에 매우 취약");
        assertThat(aiEvaluationDto.aiStrategy()).isEqualTo("~~");
    }

    @Test
    @DisplayName("EnvironmentConditionInfo 객체 생성 검증")
    void environmentConditionInfoTest() {
        EnvironmentConditionInfo info = new EnvironmentConditionInfo("20-25℃", "85-90%",
                "1000ppm", "500lux");

        assertThat(info.temperature()).isEqualTo("20-25℃");
        assertThat(info.humidity()).isEqualTo("85-90%");
        assertThat(info.co2()).isEqualTo("1000ppm");
        assertThat(info.illuminance()).isEqualTo("500lux");
    }

    @Test
    @DisplayName("RecipeDto 객체 생성 검증")
    void recipeDtoTest() {
        RecipeDto recipe = new RecipeDto("버섯 볶음", "간장 2큰술");

        assertThat(recipe.name()).isEqualTo("버섯 볶음");
        assertThat(recipe.instructions()).isEqualTo("간장 2큰술");
    }

    @Test
    @DisplayName("MushGuideResponse 응답 객체 조립 검증")
    void mushGuidResponseTest(){
        AiEvaluationDto eval = new AiEvaluationDto(1, 5, "민감도 없음", "전략");
        EnvironmentConditionInfo cond = new EnvironmentConditionInfo("20℃", "80%", "800ppm",
                "100lux");
        RecipeDto recipe = new RecipeDto("버섯전", "계란 옷 입히기");

        MushGuideResponse response = new MushGuideResponse(
                eval, "요약 내용", "치명적 경고", "꿀팁", cond, List.of(recipe)
        );

        assertThat(response.evaluation()).isEqualTo(eval);
        assertThat(response.summary()).isEqualTo("요약 내용");
        assertThat(response.caution()).isEqualTo("치명적 경고");
        assertThat(response.tip()).isEqualTo("꿀팁");
        assertThat(response.conditions()).isEqualTo(cond);
        assertThat(response.recipes()).hasSize(1);
    }

}
