package com.aitestforge.repository;

import com.aitestforge.domain.workspace.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Workspace> findByUserIdAndIsDefaultTrue(Long userId);
}
