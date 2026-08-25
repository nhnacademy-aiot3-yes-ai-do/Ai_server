package site.yesaido.ai_server.storage.minio.exception;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/**
 * MinIO에서 객체를 읽지 못했을 때 발생하는 예외입니다.
 */
public class MinioObjectReadException extends CustomServerException {
    public MinioObjectReadException(String objectKey, Throwable cause) {
        super(
                "MinIO 객체를 조회하지 못했습니다. objectKey=" + objectKey,
                cause,
                ServerErrorLevel.ERROR_LEVEL
        );
    }
}
