package site.yesaido.ai_server.dto.ai;

public record EnvironmentConditionInfo(
        SensorRange temperature, // ex : min: 18.0, max: 24.0
        SensorRange humidity,    // ex : min: 80.0, max: 90.0
        SensorRange co2,         // ex : min: 800.0, max: 1200.0
        SensorRange light        // ex : min: 100.0, max: 500.0
) {
}
