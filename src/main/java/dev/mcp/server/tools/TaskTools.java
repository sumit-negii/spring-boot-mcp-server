package dev.mcp.server.tools;

import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.enums.TaskPriority;
import dev.mcp.server.domain.enums.TaskStatus;
import dev.mcp.server.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTools {

    private final TaskService taskService;

    @Tool(name = "create_task", description = "Create a new task. Returns the created task with its assigned ID.")
    public Task createTask(
            @ToolParam(description = "Task title") String title,
            @ToolParam(description = "Task description", required = false) String description,
            @ToolParam(description = "Priority: LOW, MEDIUM, HIGH, or CRITICAL. Defaults to MEDIUM.", required = false) String priority,
            @ToolParam(description = "Due date in YYYY-MM-DD format", required = false) String dueDate,
            @ToolParam(description = "Project ID to assign the task to", required = false) Long projectId,
            @ToolParam(description = "User ID to assign as the task assignee", required = false) Long assigneeId) {
        log.info("[MCP] create_task: title={}", title);
        TaskPriority p = priority != null ? TaskPriority.valueOf(priority.toUpperCase()) : null;
        LocalDate d = dueDate != null ? LocalDate.parse(dueDate) : null;
        return taskService.createTask(title, description, p, d, projectId, assigneeId);
    }

    @Tool(name = "list_tasks", description = "List tasks with optional filters. All filters are optional and can be combined.")
    public List<Task> listTasks(
            @ToolParam(description = "Filter by status: TODO, IN_PROGRESS, REVIEW, DONE, or CANCELLED", required = false) String status,
            @ToolParam(description = "Filter by priority: LOW, MEDIUM, HIGH, or CRITICAL", required = false) String priority,
            @ToolParam(description = "Filter by project ID", required = false) Long projectId,
            @ToolParam(description = "Filter by assignee user ID", required = false) Long assigneeId) {
        log.info("[MCP] list_tasks: status={}, priority={}, projectId={}, assigneeId={}", status, priority, projectId, assigneeId);
        TaskStatus s = status != null ? TaskStatus.valueOf(status.toUpperCase()) : null;
        TaskPriority p = priority != null ? TaskPriority.valueOf(priority.toUpperCase()) : null;
        return taskService.listTasks(s, p, projectId, assigneeId);
    }

    @Tool(name = "get_task", description = "Get full details of a specific task by its ID.")
    public Task getTask(
            @ToolParam(description = "The task ID") Long taskId) {
        log.info("[MCP] get_task: taskId={}", taskId);
        return taskService.getTask(taskId);
    }

    @Tool(name = "update_task", description = "Update task fields. Only provided fields are changed; omitted fields remain unchanged.")
    public Task updateTask(
            @ToolParam(description = "The task ID to update") Long taskId,
            @ToolParam(description = "New title", required = false) String title,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "New priority: LOW, MEDIUM, HIGH, or CRITICAL", required = false) String priority,
            @ToolParam(description = "New due date in YYYY-MM-DD format", required = false) String dueDate) {
        log.info("[MCP] update_task: taskId={}, title={}, priority={}, dueDate={}", taskId, title, priority, dueDate);
        TaskPriority p = priority != null ? TaskPriority.valueOf(priority.toUpperCase()) : null;
        LocalDate d = dueDate != null ? LocalDate.parse(dueDate) : null;
        return taskService.updateTask(taskId, title, description, p, d);
    }

    @Tool(name = "delete_task", description = "Delete a task permanently by its ID.")
    public String deleteTask(
            @ToolParam(description = "The task ID to delete") Long taskId) {
        log.info("[MCP] delete_task: taskId={}", taskId);
        taskService.deleteTask(taskId);
        return "Task " + taskId + " deleted successfully.";
    }

    @Tool(name = "assign_task", description = "Assign a task to a specific user.")
    public Task assignTask(
            @ToolParam(description = "The task ID") Long taskId,
            @ToolParam(description = "The user ID to assign the task to") Long userId) {
        log.info("[MCP] assign_task: taskId={}, userId={}", taskId, userId);
        return taskService.assignTask(taskId, userId);
    }

    @Tool(name = "update_task_status", description = "Update the status of a task. Valid statuses: TODO, IN_PROGRESS, REVIEW, DONE, CANCELLED.")
    public Task updateTaskStatus(
            @ToolParam(description = "The task ID") Long taskId,
            @ToolParam(description = "New status: TODO, IN_PROGRESS, REVIEW, DONE, or CANCELLED") String status) {
        log.info("[MCP] update_task_status: taskId={}, status={}", taskId, status);
        return taskService.updateTaskStatus(taskId, TaskStatus.valueOf(status.toUpperCase()));
    }

    @Tool(name = "search_tasks", description = "Search for tasks by keyword. Searches in both task title and description.")
    public List<Task> searchTasks(
            @ToolParam(description = "Keyword to search for in task titles and descriptions") String keyword) {
        log.info("[MCP] search_tasks: keyword={}", keyword);
        return taskService.searchTasks(keyword);
    }
}
