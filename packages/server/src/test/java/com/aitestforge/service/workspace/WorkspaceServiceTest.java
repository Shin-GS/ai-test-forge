package com.aitestforge.service.workspace;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.workspace.Workspace;
import com.aitestforge.domain.workspace.WorkspaceMapping;
import com.aitestforge.dto.workspace.WorkspaceMappingDto;
import com.aitestforge.dto.workspace.request.CreateWorkspaceRequest;
import com.aitestforge.dto.workspace.request.UpdateWorkspaceRequest;
import com.aitestforge.dto.workspace.response.WorkspaceResponse;
import com.aitestforge.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정상: 첫 워크스페이스 생성 시 isDefault=true로 설정된다")
        void success_first_workspace_is_default() {
            // given
            Long userId = 1L;
            CreateWorkspaceRequest request = new CreateWorkspaceRequest("개발 환경", List.of(
                    new WorkspaceMappingDto("user-service", "dev")
            ));

            given(workspaceRepository.findByUserIdOrderByCreatedAtAsc(userId)).willReturn(List.of());

            // when
            WorkspaceResponse response = workspaceService.create(request, userId);

            // then
            assertThat(response.isDefault()).isTrue();
            assertThat(response.name()).isEqualTo("개발 환경");
            assertThat(response.mappings()).hasSize(1);
            then(workspaceRepository).should().save(any(Workspace.class));
        }

        @Test
        @DisplayName("정상: 두 번째 이상 워크스페이스 생성 시 isDefault=false로 설정된다")
        void success_second_workspace_is_not_default() {
            // given
            Long userId = 1L;
            CreateWorkspaceRequest request = new CreateWorkspaceRequest("테스트 환경", null);
            Workspace existingWorkspace = createWorkspace(1L, userId, "기존 환경", true);

            given(workspaceRepository.findByUserIdOrderByCreatedAtAsc(userId))
                    .willReturn(List.of(existingWorkspace));

            // when
            WorkspaceResponse response = workspaceService.create(request, userId);

            // then
            assertThat(response.isDefault()).isFalse();
            assertThat(response.name()).isEqualTo("테스트 환경");
            then(workspaceRepository).should().save(any(Workspace.class));
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("정상: 이름과 매핑을 변경하면 업데이트된 응답을 반환한다")
        void success_updates_name_and_mappings() {
            // given
            Long workspaceId = 1L;
            Workspace workspace = createWorkspace(workspaceId, 1L, "기존 이름", false);
            UpdateWorkspaceRequest request = new UpdateWorkspaceRequest("변경된 이름", List.of(
                    new WorkspaceMappingDto("payment-service", "feature-pay")
            ));

            given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));

            // when
            WorkspaceResponse response = workspaceService.update(workspaceId, request);

            // then
            assertThat(response.name()).isEqualTo("변경된 이름");
            assertThat(response.mappings()).hasSize(1);
            assertThat(response.mappings().getFirst().subdomainName()).isEqualTo("payment-service");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("실패: 기본 워크스페이스를 삭제하면 BusinessException 발생")
        void fail_delete_default_workspace() {
            // given
            Long workspaceId = 1L;
            Workspace defaultWorkspace = createWorkspace(workspaceId, 1L, "기본 환경", true);

            given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(defaultWorkspace));

            // when & then
            assertThatThrownBy(() -> workspaceService.delete(workspaceId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            then(workspaceRepository).should(org.mockito.Mockito.never()).delete(any());
        }

        @Test
        @DisplayName("정상: 기본이 아닌 워크스페이스를 삭제하면 성공한다")
        void success_delete_non_default_workspace() {
            // given
            Long workspaceId = 2L;
            Workspace workspace = createWorkspace(workspaceId, 1L, "추가 환경", false);

            given(workspaceRepository.findById(workspaceId)).willReturn(Optional.of(workspace));

            // when
            workspaceService.delete(workspaceId);

            // then
            then(workspaceRepository).should().delete(workspace);
        }
    }

    @Nested
    @DisplayName("getDefaultMappings")
    class GetDefaultMappings {

        @Test
        @DisplayName("정상: 기본 워크스페이스의 매핑 목록을 반환한다")
        void success_returns_default_workspace_mappings() {
            // given
            Long userId = 1L;
            Workspace defaultWorkspace = createWorkspaceWithMappings(1L, userId, "기본 환경", true,
                    List.of(
                            createMapping("user-service", "dev"),
                            createMapping("payment-service", "dev")
                    ));

            given(workspaceRepository.findByUserIdAndIsDefaultTrue(userId))
                    .willReturn(Optional.of(defaultWorkspace));

            // when
            List<WorkspaceMappingDto> mappings = workspaceService.getDefaultMappings(userId);

            // then
            assertThat(mappings).hasSize(2);
            assertThat(mappings.get(0).subdomainName()).isEqualTo("user-service");
            assertThat(mappings.get(1).subdomainName()).isEqualTo("payment-service");
        }

        @Test
        @DisplayName("정상: 기본 워크스페이스가 없으면 빈 목록을 반환한다")
        void success_returns_empty_when_no_default() {
            // given
            Long userId = 1L;
            given(workspaceRepository.findByUserIdAndIsDefaultTrue(userId))
                    .willReturn(Optional.empty());

            // when
            List<WorkspaceMappingDto> mappings = workspaceService.getDefaultMappings(userId);

            // then
            assertThat(mappings).isEmpty();
        }
    }

    // === Helper Methods ===

    private Workspace createWorkspace(Long id, Long userId, String name, boolean isDefault) {
        return Workspace.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .isDefault(isDefault)
                .build();
    }

    private Workspace createWorkspaceWithMappings(Long id, Long userId, String name, boolean isDefault,
                                                   List<WorkspaceMapping> mappings) {
        Workspace workspace = Workspace.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .isDefault(isDefault)
                .build();
        for (WorkspaceMapping mapping : mappings) {
            workspace.addMapping(mapping);
        }
        return workspace;
    }

    private WorkspaceMapping createMapping(String subdomainName, String environment) {
        return WorkspaceMapping.builder()
                .subdomainName(subdomainName)
                .environment(environment)
                .build();
    }
}
