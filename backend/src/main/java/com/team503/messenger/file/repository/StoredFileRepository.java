package com.team503.messenger.file.repository;

import com.team503.messenger.file.entity.StoredFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByObjectKey(String objectKey);

    boolean existsByObjectKey(String objectKey);
}
