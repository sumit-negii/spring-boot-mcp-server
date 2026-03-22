package dev.mcp.server.repository;

import dev.mcp.server.domain.User;
import dev.mcp.server.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByRole(UserRole role);
    Optional<User> findByEmail(String email);
}
