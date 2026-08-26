package site.yesaido.ai_server.storage.multipart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteArrayMultipartFileTest {
    @Test
    @DisplayName("ByteArrayMultipartFile 기본 정보 및 바이트 데이터 반환 검증")
    void testBasicMethods() {
        byte[] content = "test image content".getBytes();
        ByteArrayMultipartFile file = new ByteArrayMultipartFile("image", "mushroom.jpg", "image/jpeg", content);

        assertThat(file.getName()).isEqualTo("image");
        assertThat(file.getOriginalFilename()).isEqualTo("mushroom.jpg");
        assertThat(file.getContentType()).isEqualTo("image/jpeg");
        assertThat(file.isEmpty()).isFalse();
        assertThat(file.getSize()).isEqualTo(content.length);
        assertThat(file.getBytes()).isEqualTo(content);
        assertThat(file.getInputStream()).hasBinaryContent(content);
    }

    @Test
    @DisplayName("빈 바이트 배열로 생성 시 isEmpty는 true를 반환한다")
    void testEmptyContent() {
        ByteArrayMultipartFile file = new ByteArrayMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);
        assertThat(file.isEmpty()).isTrue();
        assertThat(file.getSize()).isZero();
    }

    @Test
    @DisplayName("생성자에 null 인자 전달 시 NullPointerException이 발생한다")
    void testConstructorNullChecks() {
        assertThatThrownBy(() -> new ByteArrayMultipartFile(null, "a.jpg", "image/jpeg", new byte[0]))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ByteArrayMultipartFile("image", null, "image/jpeg", new byte[0]))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ByteArrayMultipartFile("image", "a.jpg", null, new byte[0]))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ByteArrayMultipartFile("image", "a.jpg", "image/jpeg", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("transferTo 호출 시 대상 파일 경로로 정상 저장된다")
    void testTransferTo(@TempDir File tempDir) throws IOException {
        byte[] content = "mushroom image bytes".getBytes();
        ByteArrayMultipartFile file = new ByteArrayMultipartFile("image", "mushroom.jpg", "image/jpeg", content);

        File dest = new File(tempDir, "saved_mushroom.jpg");
        file.transferTo(dest);

        assertThat(dest).exists();
        assertThat(Files.readAllBytes(dest.toPath())).isEqualTo(content);
    }
}
