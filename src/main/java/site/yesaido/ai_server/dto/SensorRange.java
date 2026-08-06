package site.yesaido.ai_server.dto;

public record SensorRange(
        Double min,
        Double max
) {
    public SensorRange{ // .isFinite() 무한대 방지
        if(min == null || max == null || !Double.isFinite(min) || !Double.isFinite(max) || min > max){
            throw new IllegalArgumentException("잘못된 범위 저장");
        }
    }
}
