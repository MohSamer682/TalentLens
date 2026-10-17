package com.resumescreener.common.session;

import com.resumescreener.common.session.BatchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BatchJobRepository extends JpaRepository<BatchJob, String> {
    List<BatchJob> findBySessionId(String sessionId);
}