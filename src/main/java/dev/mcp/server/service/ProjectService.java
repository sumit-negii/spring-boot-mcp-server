package dev.mcp.server.service;

import dev.mcp.server.domain.Project;
import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.enums.ProjectStatus;
import dev.mcp.server.repository.ProjectRepository;
import dev.mcp.server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public Project createProject(String name, String description) {
        Project project = Project.builder()
                .name(name)
                .description(description)
                .status(ProjectStatus.ACTIVE)
                .build();
        return projectRepository.save(project);
    }

    public List<Project> listProjects(ProjectStatus status) {
        return status != null
                ? projectRepository.findByStatus(status)
                : projectRepository.findAll();
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    @Transactional
    public Project updateProject(Long id, String name, String description, ProjectStatus status) {
        Project project = getProject(id);
        if (name != null) project.setName(name);
        if (description != null) project.setDescription(description);
        if (status != null) project.setStatus(status);
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    public List<Task> getProjectTasks(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }
}
