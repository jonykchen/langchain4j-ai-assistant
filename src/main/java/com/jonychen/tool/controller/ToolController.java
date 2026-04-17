package com.jonychen.tool.controller;

import com.jonychen.model.ApiResponse;
import com.jonychen.tool.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工具系统 REST API
 *
 * @author jonychen
 */
@Tag(name = "工具管理", description = "工具注册、查询、执行相关接口")
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    /**
     * 获取所有可用工具
     */
    @Operation(summary = "获取工具列表", description = "获取所有已注册的工具定义")
    @GetMapping
    public ApiResponse<List<ToolInfo>> getAllTools() {
        List<ToolInfo> tools = toolRegistry.getAllTools().stream()
                .map(this::toToolInfo)
                .toList();
        return ApiResponse.success(tools);
    }

    /**
     * 获取指定工具详情
     */
    @Operation(summary = "获取工具详情", description = "获取指定工具的完整定义")
    @GetMapping("/{name}")
    public ApiResponse<ToolInfo> getTool(@PathVariable String name) {
        ToolDefinition tool = toolRegistry.getTool(name)
                .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + name));
        return ApiResponse.success(toToolInfo(tool));
    }

    /**
     * 按分类获取工具
     */
    @Operation(summary = "按分类获取工具", description = "获取指定分类下的所有工具")
    @GetMapping("/category/{category}")
    public ApiResponse<List<ToolInfo>> getToolsByCategory(@PathVariable String category) {
        ToolCategory cat = ToolCategory.valueOf(category.toUpperCase());
        List<ToolInfo> tools = toolRegistry.getToolsByCategory(cat).stream()
                .map(this::toToolInfo)
                .toList();
        return ApiResponse.success(tools);
    }

    /**
     * 执行工具
     */
    @Operation(summary = "执行工具", description = "手动执行指定工具")
    @PostMapping("/{name}/execute")
    public ApiResponse<ToolResult> executeTool(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> params
    ) {
        if (!toolRegistry.hasTool(name)) {
            throw new ToolNotFoundException("Tool not found: " + name);
        }

        ToolResult result = toolRegistry.execute(name, params != null ? params : Map.of());
        return ApiResponse.success(result);
    }

    /**
     * 获取工具数量
     */
    @Operation(summary = "获取工具数量", description = "获取已注册工具的总数量")
    @GetMapping("/count")
    public ApiResponse<Integer> getToolCount() {
        return ApiResponse.success(toolRegistry.size());
    }

    private ToolInfo toToolInfo(ToolDefinition def) {
        return new ToolInfo(
                def.name(),
                def.description(),
                def.category().getDisplayName(),
                def.requiredPermissions(),
                def.timeout().toMillis(),
                def.maxRetries(),
                def.parameters().toSchemaMap()
        );
    }

    /**
     * 工具信息 VO
     */
    public record ToolInfo(
            String name,
            String description,
            String category,
            List<String> requiredPermissions,
            long timeoutMs,
            int maxRetries,
            Map<String, Object> parameters
    ) {}
}