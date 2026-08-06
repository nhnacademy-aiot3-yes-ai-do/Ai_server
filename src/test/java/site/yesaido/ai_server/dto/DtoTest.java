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
        EnvironmentConditionInfo info = new EnvironmentConditionInfo(
                new SensorRange(20.0, 25.0),
                new SensorRange(85.0, 90.0),
                new SensorRange(800.0, 1000.0),
                new SensorRange(100.0, 500.0)
        );

        assertThat(info.temperature().min()).isEqualTo(20.0);
        assertThat(info.temperature().max()).isEqualTo(25.0);
        assertThat(info.humidity().min()).isEqualTo(85.0);
        assertThat(info.humidity().max()).isEqualTo(90.0);
        assertThat(info.co2().min()).isEqualTo(800.0);
        assertThat(info.co2().max()).isEqualTo(1000.0);
        assertThat(info.light().min()).isEqualTo(100.0);
        assertThat(info.light().max()).isEqualTo(500.0);
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
        EnvironmentConditionInfo cultivationCond = new EnvironmentConditionInfo(
                new SensorRange(18.0, 24.0),
                new SensorRange(80.0, 90.0),
                new SensorRange(800.0, 1200.0),
                new SensorRange(100.0, 500.0)
        );
        EnvironmentConditionInfo harvestCond = new EnvironmentConditionInfo(
                new SensorRange(15.0, 18.0),
                new SensorRange(85.0, 95.0),
                new SensorRange(1000.0, 1500.0),
                new SensorRange(100.0, 300.0)
        );
        RecipeDto recipe = new RecipeDto("버섯전", "계란 옷 입히기");

        MushGuideResponse response = new MushGuideResponse(
                1L, "느타리버섯", eval, "요약 내용", "치명적 경고", "꿀팁",
                cultivationCond, harvestCond, List.of(recipe)
        );

        assertThat(response.mushroomId()).isEqualTo(1L);
        assertThat(response.mushroomName()).isEqualTo("느타리버섯");
        assertThat(response.evaluation()).isEqualTo(eval);
        assertThat(response.summary()).isEqualTo("요약 내용");
        assertThat(response.caution()).isEqualTo("치명적 경고");
        assertThat(response.tip()).isEqualTo("꿀팁");
        assertThat(response.cultivationCondition()).isEqualTo(cultivationCond);
        assertThat(response.harvestCondition()).isEqualTo(harvestCond);
        assertThat(response.recipes()).hasSize(1);
    }

}
