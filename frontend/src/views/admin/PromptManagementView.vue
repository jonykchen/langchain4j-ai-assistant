<template>
  <div class="prompt-management">
    <!-- 顶部操作栏 -->
    <div class="mb-4 flex justify-between items-center">
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        创建模板
      </el-button>
      <el-input
        v-model="searchName"
        placeholder="搜索模板名称"
        style="width: 300px"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 模板列表 -->
    <el-row :gutter="20">
      <el-col
        v-for="prompt in promptGroups"
        :key="prompt.name"
        :span="12"
        class="mb-4"
      >
        <el-card shadow="hover" class="prompt-card">
          <template #header>
            <div class="flex justify-between items-center">
              <div>
                <span class="font-bold">{{ prompt.name }}</span>
                <el-tag v-if="prompt.activeVersion" type="success" size="small" class="ml-2">
                  活跃: {{ prompt.activeVersion }}
                </el-tag>
              </div>
              <div>
                <el-button link type="primary" @click="showVersions(prompt.name)">
                  版本历史
                </el-button>
              </div>
            </div>
          </template>

          <div class="prompt-content">
            <p class="text-gray-600 mb-2">{{ prompt.description || '暂无描述' }}</p>
            <div class="text-sm text-gray-400">
              版本数: {{ prompt.versions.length }} |
              最后更新: {{ formatTime(prompt.updatedAt) }}
            </div>
          </div>

          <template #footer>
            <el-button-group>
              <el-button size="small" @click="showEditDialog(prompt)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="warning" @click="showABTestDialog(prompt)">
                A/B 测试
              </el-button>
            </el-button-group>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="promptGroups.length === 0" description="暂无 Prompt 模板" />

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      :title="editingPrompt ? '创建新版本' : '创建模板'"
      width="60%"
      destroy-on-close
    >
      <el-form :model="promptForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="promptForm.name" :disabled="!!editingPrompt" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="promptForm.version" placeholder="如 1.0.0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="promptForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="模板内容" required>
          <el-input
            v-model="promptForm.content"
            type="textarea"
            :rows="10"
            placeholder="使用 {{variable}} 定义变量"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="promptForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog
      v-model="versionDialogVisible"
      :title="`版本历史 - ${selectedPromptName}`"
      width="80%"
      destroy-on-close
    >
      <el-table :data="versions" stripe>
        <el-table-column prop="version" label="版本" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.active" type="success">{{ row.version }}</el-tag>
            <el-tag v-else-if="row.production" type="warning">{{ row.version }}</el-tag>
            <span v-else>{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="A/B 测试" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.abTestEnabled" type="info" size="small">
              {{ row.abTestTrafficPercentage }}%
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button link type="primary" @click="viewVersionContent(row)">
                查看
              </el-button>
              <el-button
                link
                type="success"
                @click="activateVersion(row)"
                :disabled="row.active"
              >
                激活
              </el-button>
              <el-button
                link
                type="warning"
                @click="promoteToProduction(row)"
                :disabled="row.production"
              >
                推送生产
              </el-button>
              <el-button
                link
                type="danger"
                @click="rollbackVersion(row)"
                :disabled="row.active"
              >
                回滚
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- A/B 测试配置对话框 -->
    <el-dialog
      v-model="abTestDialogVisible"
      title="配置 A/B 测试"
      width="50%"
      destroy-on-close
    >
      <el-form :model="abTestForm" label-width="120px">
        <el-form-item label="基线版本">
          <el-select v-model="abTestForm.baselineVersion" placeholder="选择基线版本">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="v.version"
              :value="v.version"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实验版本">
          <el-select v-model="abTestForm.variantVersion" placeholder="选择实验版本">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="v.version"
              :value="v.version"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实验名称">
          <el-input v-model="abTestForm.variantName" placeholder="如 experiment-v2" />
        </el-form-item>
        <el-form-item label="流量分配 (%)">
          <el-slider v-model="abTestForm.trafficPercentage" :min="1" :max="50" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="abTestDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="configureABTest">启动 A/B 测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { observabilityApi, type PromptTemplate } from '@/api/observability'

const prompts = ref<PromptTemplate[]>([])
const versions = ref<PromptTemplate[]>([])
const searchName = ref('')
const selectedPromptName = ref('')

const createDialogVisible = ref(false)
const versionDialogVisible = ref(false)
const abTestDialogVisible = ref(false)
const editingPrompt = ref<PromptTemplate | null>(null)

const promptForm = reactive({
  name: '',
  version: '',
  description: '',
  content: '',
  tags: ''
})

const abTestForm = reactive({
  baselineVersion: '',
  variantVersion: '',
  variantName: '',
  trafficPercentage: 10
})

// 按名称分组模板
const promptGroups = computed(() => {
  const groups = new Map<string, {
    name: string
    description: string
    activeVersion: string | null
    versions: PromptTemplate[]
    updatedAt: string
  }>()

  for (const p of prompts.value) {
    if (!groups.has(p.name)) {
      groups.set(p.name, {
        name: p.name,
        description: p.description,
        activeVersion: p.active ? p.version : null,
        versions: [],
        updatedAt: p.createdAt
      })
    }

    const group = groups.get(p.name)!
    group.versions.push(p)

    if (p.active) {
      group.activeVersion = p.version
    }
    if (new Date(p.createdAt) > new Date(group.updatedAt)) {
      group.updatedAt = p.createdAt
    }
  }

  return Array.from(groups.values())
})

const loadPrompts = async () => {
  try {
    // 获取所有模板名称，然后加载每个名称的版本
    const names = await observabilityApi.getPromptNames()
    const allTemplates: PromptTemplate[] = []
    for (const name of names) {
      try {
        const templateVersions = await observabilityApi.getPromptVersions(name)
        allTemplates.push(...templateVersions)
      } catch {
        // 忽略单个模板加载失败
      }
    }
    prompts.value = allTemplates
  } catch (error) {
    ElMessage.error('加载模板列表失败')
  }
}

const showCreateDialog = () => {
  editingPrompt.value = null
  Object.assign(promptForm, {
    name: '',
    version: '1.0.0',
    description: '',
    content: '',
    tags: ''
  })
  createDialogVisible.value = true
}

const showEditDialog = (prompt: { name: string; versions: PromptTemplate[] }) => {
  const latestVersion = prompt.versions[0]
  editingPrompt.value = latestVersion
  Object.assign(promptForm, {
    name: prompt.name,
    version: '',
    description: '',
    content: latestVersion.content,
    tags: latestVersion.tags
  })
  createDialogVisible.value = true
}

const savePrompt = async () => {
  if (!promptForm.name || !promptForm.content) {
    ElMessage.warning('请填写模板名称和内容')
    return
  }

  try {
    if (editingPrompt.value) {
      // 创建新版本
      await observabilityApi.createVersion(promptForm.name, {
        content: promptForm.content,
        description: promptForm.description
      })
    } else {
      // 创建新模板
      await observabilityApi.createPrompt({
        name: promptForm.name,
        version: promptForm.version || '1.0.0',
        description: promptForm.description,
        content: promptForm.content,
        tags: promptForm.tags
      })
    }
    ElMessage.success('保存成功')
    createDialogVisible.value = false
    loadPrompts()
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const showVersions = async (name: string) => {
  selectedPromptName.value = name
  try {
    versions.value = await observabilityApi.getPromptVersions(name)
    versionDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载版本历史失败')
  }
}

const activateVersion = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要激活版本 ${version.version} 吗？`,
      '确认激活',
      { type: 'warning' }
    )
    await observabilityApi.activateVersion(selectedPromptName.value, version.version)
    ElMessage.success('版本已激活')
    showVersions(selectedPromptName.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('激活失败')
    }
  }
}

const promoteToProduction = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要将版本 ${version.version} 推送到生产环境吗？`,
      '确认推送',
      { type: 'warning' }
    )
    await observabilityApi.promoteToProduction(selectedPromptName.value, version.version)
    ElMessage.success('已推送生产')
    showVersions(selectedPromptName.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('推送失败')
    }
  }
}

const rollbackVersion = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要回滚到版本 ${version.version} 吗？`,
      '确认回滚',
      { type: 'warning' }
    )
    await observabilityApi.rollbackVersion(selectedPromptName.value, version.version)
    ElMessage.success('回滚成功')
    showVersions(selectedPromptName.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('回滚失败')
    }
  }
}

const showABTestDialog = async (prompt: { name: string; activeVersion: string | null; versions: PromptTemplate[] }) => {
  selectedPromptName.value = prompt.name
  try {
    versions.value = await observabilityApi.getPromptVersions(prompt.name)
    abTestForm.baselineVersion = prompt.activeVersion || versions.value[0]?.version || ''
    abTestForm.variantVersion = versions.value[1]?.version || ''
    abTestForm.variantName = 'experiment-' + Date.now()
    abTestDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载版本失败')
  }
}

const configureABTest = async () => {
  if (!abTestForm.baselineVersion || !abTestForm.variantVersion) {
    ElMessage.warning('请选择基线版本和实验版本')
    return
  }

  try {
    await observabilityApi.configureABTest(selectedPromptName.value, {
      baselineVersion: abTestForm.baselineVersion,
      variantVersion: abTestForm.variantVersion,
      variantName: abTestForm.variantName,
      trafficPercentage: abTestForm.trafficPercentage
    })
    ElMessage.success('A/B 测试已配置')
    abTestDialogVisible.value = false
    loadPrompts()
  } catch (error) {
    ElMessage.error('配置失败')
  }
}

const viewVersionContent = (version: PromptTemplate) => {
  ElMessageBox.alert(
    `<pre style="max-height:400px;overflow:auto;white-space:pre-wrap">${version.content}</pre>`,
    `版本 ${version.version} 内容`,
    { dangerouslyUseHTMLString: true }
  )
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadPrompts()
})
</script>

<style scoped>
.prompt-card {
  height: 100%;
}

.prompt-content {
  min-height: 80px;
}

.mb-4 {
  margin-bottom: 16px;
}

.ml-2 {
  margin-left: 8px;
}
</style>
