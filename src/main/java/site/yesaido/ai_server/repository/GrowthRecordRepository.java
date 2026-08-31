package site.yesaido.ai_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.yesaido.ai_server.entity.GrowthRecord;

import java.util.Optional;

@Repository
public interface GrowthRecordRepository extends JpaRepository<GrowthRecord, Long> {

    Optional<GrowthRecord> findByCultivationPhotoId(Long cultivationPhotoId);
}
