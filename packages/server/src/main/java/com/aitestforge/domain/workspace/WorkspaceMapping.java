package com.aitestforge.domain.workspace;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "WORKSPACE_MAPPING")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WorkspaceMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WORKSPACE_ID", nullable = false)
    private Workspace workspace;

    @Column(name = "SUBDOMAIN_NAME", nullable = false)
    private String subdomainName;

    @Column(name = "ENVIRONMENT", nullable = false)
    private String environment;
}
