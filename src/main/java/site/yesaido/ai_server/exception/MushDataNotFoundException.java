package site.yesaido.ai_server.exception;

public class MushDataNotFoundException extends RuntimeException {
    public MushDataNotFoundException(Long mushroomId) {
        super("해당 ID의 버섯 데이터가 존재하지 않습니다. 요청 ID : " + mushroomId);
    }
}
