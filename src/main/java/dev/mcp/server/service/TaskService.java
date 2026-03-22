package dev.mcp.server.service;

import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.enums.TaskPriority;
import dev.mcp.server.domain.enums.TaskStatus;
import dev.mcp.server.repository.ProjectRepository;
import dev.mcp.server.repository.TaskRepository;
import dev.mcp.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public Task createTask(String title, String description, TaskPriority priority,
                           LocalDate dueDate, Long projectId, Long assigneeId) {
        Task task = Task.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority : TaskPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .dueDate(dueDate)
                .project(projectId != null
                        ? projectRepository.findById(projectId).orElse(null) : null)
                .assignee(assigneeId != null
                        ? userRepository.findById(assigneeId).orElse(null) : null)
                .build();
        return taskRepository.save(task);
    }

    public List<Task> listTasks(TaskStatus status, TaskPriority priority,
                                Long projectId, Long assigneeId) {
        if (assigneeId != null) {
            return taskRepository.findByAssigneeId(assigneeId).stream()
                    .filter(t -> status == null || t.getStatus() == status)
                    .filter(t -> priority == null || t.getPriority() == priority)
                    .filter(t -> projectId == null ||
                            (t.getProject() != null && t.getProject().getId().equals(projectId)))
                    .toList();
        }
        if (status != null && priority != null && projectId != null)
            return taskRepository.findByStatusAndPriorityAndProjectId(status, priority, projectId);
        if (status != null && priority != null)
            return taskRepository.findByStatusAndPriority(status, priority);
        if (status != null && projectId != null)
            return taskRepository.findByStatusAndProjectId(status, projectId);
        if (priority != null && projectId != null)
            return taskRepository.findByPriorityAndProjectId(priority, projectId);
        if (status != null) return taskRepository.findByStatus(status);
        if (priority != null) return taskRepository.findByPriority(priority);
        if (projectId != null) return taskRepository.findByProjectId(projectId);
        return taskRepository.findAll();
    }

    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    @Transactional
    public Task updateTask(Long id, String title, String description,
                           TaskPriority priority, LocalDate dueDate) {
        Task task = getTask(id);
        if (title != null) task.setTitle(title);
        if (description != null) task.setDescription(description);
        if (priority != null) task.setPriority(priority);
        if (dueDate != null) task.setDueDate(dueDate);
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    @Transactional
    public Task assignTask(Long taskId, Long userId) {
        Task task = getTask(taskId);
        task.setAssignee(userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId)));
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTaskStatus(Long taskId, TaskStatus status) {
        Task task = getTask(taskId);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    public List<Task> searchTasks(String keyword) {
        return taskRepository.searchByKeyword(keyword);
    }

    public List<Task> getTasksByUser(Long userId) {
        return taskRepository.findByAssigneeId(userId);
    }
}
