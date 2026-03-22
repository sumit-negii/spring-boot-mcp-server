package dev.mcp.server.repository;

import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.enums.TaskPriority;
import dev.mcp.server.domain.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByStatus(TaskStatus status);
    List<Task> findByPriority(TaskPriority priority);
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByStatusAndPriority(TaskStatus status, TaskPriority priority);
    List<Task> findByStatusAndProjectId(TaskStatus status, Long projectId);
    List<Task> findByPriorityAndProjectId(TaskPriority priority, Long projectId);
    List<Task> findByStatusAndPriorityAndProjectId(TaskStatus status, TaskPriority priority, Long projectId);

    @Query("SELECT t FROM Task t WHERE " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Task> searchByKeyword(@Param("keyword") String keyword);
}
