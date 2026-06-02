<script setup lang="ts">
/**
 * AgentSelector 组件
 *
 * <p>Agent 下拉选择器，根据用户权限过滤可见 Agent。
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>显示可用 Agent 列表（下拉选择）</li>
 *   <li>根据用户角色过滤：ADMIN 可见所有 Agent，USER 仅见 USER 级别</li>
 *   <li>显示 Agent 描述信息</li>
 * </ul>
 *
 * @author jonychen
 */
import { computed } from 'vue'
import type { AgentMetadata } from '@/types/agent'

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** 可用 Agent 列表 */
    agents: AgentMetadata[]
    /** 当前选中的 Agent 名称 */
    modelValue?: string
    /** 当前用户角色 */
    userRole?: string
    /** 是否正在加载 */
    loading?: boolean
  }>(),
  {
    modelValue: undefined,
    userRole: 'USER',
    loading: false,
  }
)

/** Events 定义 */
const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void
  (e: 'select', agent: AgentMetadata): void
}>()

/** Agent 类型图标映射 */
const agentTypeIcons: Record<string, string> = {
  OPS: 'Setting',
  DATA: 'DataAnalysis',
  CHAT: 'ChatDotRound',
}

/** Agent 类型颜色映射 */
const agentTypeColors: Record<string, string> = {
  OPS: '#e6a23c',
  DATA: '#409eff',
  CHAT: '#67c23a',
}

/**
 * 根据用户权限过滤 Agent
 *
 * <p>ADMIN 角色可见所有 Agent，USER 角色仅可见权限要求不含 ADMIN 的 Agent。
 */
const visibleAgents = computed(() => {
  if (props.userRole === 'ADMIN') return props.agents

  return props.agents.filter(agent => !agent.requiredPermissions.includes('ADMIN'))
})

/** 当前选中的 Agent 详情 */
const selectedAgent = computed(() => props.agents.find(a => a.name === props.modelValue))

/** 处理选择变化 */
function handleChange(value: string | undefined) {
  emit('update:modelValue', value)

  if (value) {
    const agent = props.agents.find(a => a.name === value)
    if (agent) emit('select', agent)
  }
}
</script>

<template>
  <div class="agent-selector">
    <el-select
      :model-value="modelValue"
      placeholder="选择 Agent（自动路由）"
      clearable
      :loading="loading"
      @update:model-value="handleChange"
    >
      <el-option
        v-for="agent in visibleAgents"
        :key="agent.name"
        :label="agent.displayName"
        :value="agent.name"
      >
        <div class="agent-option">
          <div class="option-left">
            <el-icon :style="{ color: agentTypeColors[agent.agentType] || 'var(--text-tertiary)' }">
              <component :is="agentTypeIcons[agent.agentType] || 'User'" />
            </el-icon>
            <span class="agent-name">{{ agent.displayName }}</span>
          </div>
          <span class="agent-perms">
            <el-tag v-if="agent.requiredPermissions.includes('ADMIN')" type="warning" size="small">
              ADMIN
            </el-tag>
          </span>
        </div>
      </el-option>
    </el-select>

    <!-- 选中的 Agent 描述 -->
    <div v-if="selectedAgent" class="agent-description">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ selectedAgent.description }}</span>
    </div>
  </div>
</template>

<style scoped>
.agent-selector {
  width: 100%;
}

.agent-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.option-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-name {
  font-weight: 500;
}

.agent-perms {
  margin-left: 12px;
}

.agent-description {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-secondary);
}

.agent-description .el-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
}
</style>
