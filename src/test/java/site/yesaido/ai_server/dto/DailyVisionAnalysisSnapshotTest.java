package site.yesaido.ai_server.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.yesaido.ai_server.dto.daily_feedback.DailyVisionAnalysisSnapshot;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class DailyVisionAnalysisSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("사진이 없는 경우 withoutPhoto() 정상 생성")
    void withoutPhoto_success() {
        DailyVisionAnalysisSnapshot snapshot = DailyVisionAnalysisSnapshot.withoutPhoto(1L);

        assertThat(snapshot.cultivationId()).isEqualTo(1L);
        assertThat(snapshot.hasVisionAnalysis()).isFalse();
        assertThat(snapshot.growthRecordId()).isNull();
        assertThat(snapshot.analysisData()).isNull();
    }

    @Test
    @DisplayName("사진이 있는 경우 analyzed() 정상 생성")
    void analyzed_success() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", "HEALTHY");
        LocalDateTime now = LocalDateTime.now();

        DailyVisionAnalysisSnapshot snapshot = DailyVisionAnalysisSnapshot.analyzed(
                1L, 10L, 100L, node, now
        );

        assertThat(snapshot.cultivationId()).isEqualTo(1L);
        assertThat(snapshot.hasVisionAnalysis()).isTrue();
        assertThat(snapshot.growthRecordId()).isEqualTo(10L);
        assertThat(snapshot.cultivationPhotoId()).isEqualTo(100L);
        assertThat(snapshot.analysisData()).isNotNull();
        assertThat(snapshot.analysisData().path("status").asText()).isEqualTo("HEALTHY");
        assertThat(snapshot.analyzedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("유효성 검증 실패 시 IllegalArgumentException")
    void invalidParams() {
        ObjectNode node = objectMapper.createObjectNode();
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> DailyVisionAnalysisSnapshot.withoutPhoto(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DailyVisionAnalysisSnapshot.analyzed(null, 10L, 100L, node, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
