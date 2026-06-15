package com.aitestforge.repository;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubdomainSpecRepository extends JpaRepository<SubdomainSpec, Long> {

    Optional<SubdomainSpec> findByNameAndEnvironment(String name, String environment);

    List<SubdomainSpec> findByStatus(SpecStatus status);

    List<SubdomainSpec> findByLastHeartbeatAtBefore(LocalDateTime threshold);
}
