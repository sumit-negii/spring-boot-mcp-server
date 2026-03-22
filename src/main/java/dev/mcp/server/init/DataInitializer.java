package dev.mcp.server.init;

import dev.mcp.server.domain.Project;
import dev.mcp.server.domain.Task;
import dev.mcp.server.domain.User;
import dev.mcp.server.domain.enums.ProjectStatus;
import dev.mcp.server.domain.enums.TaskPriority;
import dev.mcp.server.domain.enums.TaskStatus;
import dev.mcp.server.domain.enums.UserRole;
import dev.mcp.server.repository.ProjectRepository;
import dev.mcp.server.repository.TaskRepository;
import dev.mcp.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @Override
    public void run(String... args) {
        log.info("[DataInitializer] Seeding sample data...");

        // Users
        User alice = userRepository.save(User.builder()
                .name("Alice Chen").email("alice@example.com").role(UserRole.MANAGER).build());
        User bob = userRepository.save(User.builder()
                .name("Bob Smith").email("bob@example.com").role(UserRole.DEVELOPER).build());
        User carol = userRepository.save(User.builder()
                .name("Carol Davis").email("carol@example.com").role(UserRole.DEVELOPER).build());
        User dave = userRepository.save(User.builder()
                .name("Dave Wilson").email("dave@example.com").role(UserRole.ADMIN).build());

        // Projects
        Project mobileApp = projectRepository.save(Project.builder()
                .name("Mobile App Redesign")
                .description("Complete overhaul of the mobile application UI/UX")
                .status(ProjectStatus.ACTIVE).build());
        Project apiPlatform = projectRepository.save(Project.builder()
                .name("API Platform v2")
                .description("New REST API platform with OpenAPI spec and rate limiting")
                .status(ProjectStatus.ACTIVE).build());
        Project legacyMigration = projectRepository.save(Project.builder()
                .name("Legacy System Migration")
                .description("Migrate from monolith to microservices architecture")
                .status(ProjectStatus.ARCHIVED).build());

        // Tasks
        taskRepository.save(Task.builder()
                .title("Design new login screen")
                .description("Create wireframes and mockups for the updated login flow")
                .status(TaskStatus.IN_PROGRESS).priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(7))
                .project(mobileApp).assignee(carol).build());

        taskRepository.save(Task.builder()
                .title("Implement JWT authentication")
                .description("Add JWT-based auth to the new API endpoints")
                .status(TaskStatus.TODO).priority(TaskPriority.CRITICAL)
                .dueDate(LocalDate.now().plusDays(3))
                .project(apiPlatform).assignee(bob).build());

        taskRepository.save(Task.builder()
                .title("Write API documentation")
                .description("Document all v2 API endpoints using OpenAPI 3.0 specification")
                .status(TaskStatus.TODO).priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(14))
                .project(apiPlatform).assignee(alice).build());

        taskRepository.save(Task.builder()
                .title("Set up CI/CD pipeline")
                .description("Configure GitHub Actions for automated build, test, and deploy")
                .status(TaskStatus.REVIEW).priority(TaskPriority.HIGH)
                .dueDate(LocalDate.now().plusDays(2))
                .project(mobileApp).assignee(bob).build());

        taskRepository.save(Task.builder()
                .title("Performance testing")
                .description("Run load tests against the new API and document baseline metrics")
                .status(TaskStatus.TODO).priority(TaskPriority.MEDIUM)
                .dueDate(LocalDate.now().plusDays(21))
                .project(apiPlatform).assignee(carol).build());

        taskRepository.save(Task.builder()
                .title("Database schema audit")
                .description("Review legacy schema and plan migration to normalized structure")
                .status(TaskStatus.DONE).priority(TaskPriority.LOW)
                .project(legacyMigration).assignee(dave).build());

        taskRepository.save(Task.builder()
                .title("Fix null pointer in user profile")
                .description("NPE occurs when user has no profile picture set — needs null check")
                .status(TaskStatus.TODO).priority(TaskPriority.CRITICAL)
                .dueDate(LocalDate.now().plusDays(1))
                .project(mobileApp).assignee(bob).build());

        log.info("[DataInitializer] Sample data created: {} users, {} projects, {} tasks",
                userRepository.count(), projectRepository.count(), taskRepository.count());
    }
}
