package com.app.backend.files.persistence;

import com.app.backend.files.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Modifying
    @Query("delete from StoredFile f where f.id = :id")
    int removeById(@Param("id") long id);
}
