package dev.mcp.server.config;

import dev.mcp.server.tools.ProjectTools;
import dev.mcp.server.tools.TaskTools;
import dev.mcp.server.tools.UserTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider taskManagementToolCallbackProvider(
            TaskTools taskTools,
            ProjectTools projectTools,
            UserTools userTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(taskTools, projectTools, userTools)
                .build();
    }
}
