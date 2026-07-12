package com.resumescreener.common.repository;

import com.resumescreener.common.entity.BatchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchJobRepository extends JpaRepository<BatchJob, String> {
    List<BatchJob> findBySessionId(String sessionId);
}