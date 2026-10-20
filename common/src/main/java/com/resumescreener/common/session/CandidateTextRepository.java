package com.resumescreener.common.session;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateTextRepository extends JpaRepository<CandidateText, String> {
}
