package dev.mcp.server.tools;

import dev.mcp.server.domain.Project;
import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.enums.ProjectStatus;
import dev.mcp.server.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectTools {

    private final ProjectService projectService;

    @Tool(name = "create_project", description = "Create a new project. New projects start with ACTIVE status.")
    public Project createProject(
            @ToolParam(description = "Project name") String name,
            @ToolParam(description = "Project description", required = false) String description) {
        log.info("[MCP] create_project: name={}", name);
        return projectService.createProject(name, description);
    }

    @Tool(name = "list_projects", description = "List all projects, optionally filtered by status (ACTIVE or ARCHIVED).")
    public List<Project> listProjects(
            @ToolParam(description = "Filter by status: ACTIVE or ARCHIVED", required = false) String status) {
        log.info("[MCP] list_projects: status={}", status);
        ProjectStatus s = status != null ? ProjectStatus.valueOf(status.toUpperCase()) : null;
        return projectService.listProjects(s);
    }

    @Tool(name = "get_project_with_tasks", description = "Get a project's full details along with all its tasks.")
    public Map<String, Object> getProjectWithTasks(
            @ToolParam(description = "The project ID") Long projectId) {
        log.info("[MCP] get_project_with_tasks: projectId={}", projectId);
        Project project = projectService.getProject(projectId);
        List<Task> tasks = projectService.getProjectTasks(projectId);
        Map<String, Object> result = new HashMap<>();
        result.put("project", project);
        result.put("tasks", tasks);
        result.put("taskCount", tasks.size());
        return result;
    }

    @Tool(name = "update_project", description = "Update a project's name, description, or status. Only provided fields are changed.")
    public Project updateProject(
            @ToolParam(description = "The project ID to update") Long projectId,
            @ToolParam(description = "New name", required = false) String name,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New status: ACTIVE or ARCHIVED", required = false) String status) {
        log.info("[MCP] update_project: projectId={}, name={}, status={}", projectId, name, status);
        ProjectStatus s = status != null ? ProjectStatus.valueOf(status.toUpperCase()) : null;
        return projectService.updateProject(projectId, name, description, s);
    }

    @Tool(name = "delete_project", description = "Delete a project permanently by its ID.")
    public String deleteProject(
            @ToolParam(description = "The project ID to delete") Long projectId) {
        log.info("[MCP] delete_project: projectId={}", projectId);
        projectService.deleteProject(projectId);
        return "Project " + projectId + " deleted successfully.";
    }
}
