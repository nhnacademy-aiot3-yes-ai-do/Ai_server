package site.yesaido.ai_server.dto.front;

import java.math.BigDecimal;

public record SensorValidationResponse(
        boolean isValid,
        String message,
        BigDecimal recommendedMin,
        BigDecimal recommendedMax
) {
}
