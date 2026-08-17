package site.yesaido.ai_server.storage.multipart;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

/**
 * 메모리에 저장된 바이트 배열을 MultipartFile 형태로 제공하는 클래스입니다.
 */
public final class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /**
     *
     * @param name              multipart 요청의 파트 이름
     * @param originalFilename  원본 파일명
     * @param contentType       파일의 MIME 타입
     * @param content           파일 바이트 데이터
     */
    public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = Objects.requireNonNull(name);
        this.originalFilename = Objects.requireNonNull(originalFilename);
        this.contentType = Objects.requireNonNull(contentType);
        this.content = Objects.requireNonNull(content).clone();
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), content);
    }
}
