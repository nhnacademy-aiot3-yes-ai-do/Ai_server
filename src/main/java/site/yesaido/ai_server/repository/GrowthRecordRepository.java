package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.util.Optional;

public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {

    Optional<GrowthRecord> findByCultivationPhotoId(Long cultivationPhotoId);
}
