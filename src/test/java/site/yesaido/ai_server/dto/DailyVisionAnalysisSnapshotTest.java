package site.yesaido.ai_server.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    @DisplayName("사진이 없는 경우 withoutPhoto() 정상 생성 및 방어적 동작 검증")
    void withoutPhoto_success() {
        DailyVisionAnalysisSnapshot snapshot = DailyVisionAnalysisSnapshot.withoutPhoto(1L);

        assertThat(snapshot.cultivationId()).isEqualTo(1L);
        assertThat(snapshot.hasVisionAnalysis()).isFalse();
        assertThat(snapshot.growthRecordId()).isNull();
        assertThat(snapshot.cultivationPhotoId()).isNull();
        assertThat(snapshot.analysisData()).isNull();
        assertThat(snapshot.analyzedAt()).isNull();
    }

    @Test
    @DisplayName("사진이 있는 경우 analyzed() 정상 생성 및 방어적 복사 검증")
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

        // 방어적 복사 확인 (반환된 복사본 수정 시 원본 불변)
        ((ObjectNode) snapshot.analysisData()).put("status", "MODIFIED");
        assertThat(snapshot.analysisData().path("status").asText()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("유효성 검증 실패 시 IllegalArgumentException")
    void invalidParams() {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        LocalDateTime now = LocalDateTime.now();

        // 1. cultivationId <= 0 / null
        assertThatThrownBy(() -> DailyVisionAnalysisSnapshot.withoutPhoto(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DailyVisionAnalysisSnapshot.withoutPhoto(0L))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. hasVisionAnalysis=false 인데 분석 필드가 존재하는 경우
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, false, 10L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. hasVisionAnalysis=true 인데 필수 필드 누락/유효하지 않음
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, true, null, 100L, node, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, true, 10L, null, node, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, true, 10L, 100L, null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, true, 10L, 100L, arrayNode, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyVisionAnalysisSnapshot(1L, true, 10L, 100L, node, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
