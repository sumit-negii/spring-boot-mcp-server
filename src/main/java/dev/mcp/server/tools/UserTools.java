package dev.mcp.server.tools;

import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.User;
import dev.mcp.server.domain.enums.UserRole;
import dev.mcp.server.repository.UserRepository;
import dev.mcp.server.service.TaskService;
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
public class UserTools {

    private final UserRepository userRepository;
    private final TaskService taskService;

    @Tool(name = "list_users", description = "List all users, optionally filtered by role (ADMIN, DEVELOPER, or MANAGER).")
    public List<User> listUsers(
            @ToolParam(description = "Filter by role: ADMIN, DEVELOPER, or MANAGER", required = false) String role) {
        log.info("[MCP] list_users: role={}", role);
        UserRole r = role != null ? UserRole.valueOf(role.toUpperCase()) : null;
        return r != null ? userRepository.findByRole(r) : userRepository.findAll();
    }

    @Tool(name = "get_user_tasks", description = "Get a user's profile and all tasks currently assigned to them.")
    public Map<String, Object> getUserTasks(
            @ToolParam(description = "The user ID") Long userId) {
        log.info("[MCP] get_user_tasks: userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<Task> tasks = taskService.getTasksByUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("assignedTasks", tasks);
        result.put("taskCount", tasks.size());
        return result;
    }
}
