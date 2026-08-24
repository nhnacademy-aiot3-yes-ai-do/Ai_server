package site.yesaido.ai_server.exception;

import site.yesaido.common.exception.client.NotFoundException;

public class MushDataNotFoundException extends NotFoundException {
    public MushDataNotFoundException(Long mushroomId) {
        super("해당 ID의 버섯 데이터가 존재하지 않습니다. 요청 ID : %d".formatted(mushroomId));
    }
}
