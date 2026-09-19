import { computed, onMounted, ref } from 'vue';
import { flowApi, type FlowDefinitionVO, type FlowVersionVO } from '../../../api/flowApi';
import { confirmAction, errorText } from '../../../utils/feedback';
import { parseFlowDefinition } from '../model/flowDefinition';

/** Owns backend flow/version selection and explicit draft/publication outcomes. */
export function useFlowEditor() {
  const flows = ref<FlowDefinitionVO[]>([]);
  const selectedFlow = ref<FlowDefinitionVO | null>(null);
  const versions = ref<FlowVersionVO[]>([]);
  const selectedVersion = ref('');
  const definition = ref('');
  const savedDefinition = ref('');
  const loading = ref(false);
  const pending = ref(false);
  const error = ref('');
  const outcome = ref('');
  const dirty = computed(() => definition.value !== savedDefinition.value);
  const version = computed(() => versions.value.find(item => item.version === selectedVersion.value));

  function applyVersion(value: string) {
    selectedVersion.value = value;
    const record = versions.value.find(item => item.version === value);
    definition.value = record?.definitionJson || '';
    savedDefinition.value = definition.value;
  }

  async function canDiscard() {
    return !dirty.value || await confirmAction('当前定义尚未保存，确认放弃这些修改？');
  }

  async function refreshVersions(preferred?: string) {
    if (!selectedFlow.value) return;
    versions.value = await flowApi.getVersions(selectedFlow.value.flowKey);
    applyVersion(versions.value.find(item => item.version === preferred)?.version
      || versions.value.find(item => item.publishStatus === 'DRAFT')?.version
      || versions.value[0]?.version || '');
  }

  async function selectFlow(flow: FlowDefinitionVO) {
    if (loading.value || pending.value || !await canDiscard()) return;
    loading.value = true;
    error.value = '';
    outcome.value = '';
    selectedFlow.value = flow;
    versions.value = [];
    applyVersion('');
    try { await refreshVersions(); }
    catch (cause) { error.value = errorText(cause); }
    finally { loading.value = false; }
  }

  async function selectVersion(value: string) {
    if (loading.value || pending.value || !await canDiscard()) return;
    applyVersion(value);
    outcome.value = '';
  }

  async function reload() {
    if (loading.value || pending.value || !await canDiscard()) return;
    loading.value = true;
    error.value = '';
    try {
      flows.value = await flowApi.list();
      selectedFlow.value = flows.value.find(item => item.flowKey === selectedFlow.value?.flowKey)
        || flows.value[0] || null;
      versions.value = [];
      applyVersion('');
      await refreshVersions();
    } catch (cause) { error.value = errorText(cause); }
    finally { loading.value = false; }
  }

  async function saveDraft() {
    if (!selectedFlow.value || pending.value || loading.value) return;
    error.value = '';
    try { parseFlowDefinition(definition.value); }
    catch (cause) { error.value = errorText(cause); return; }
    pending.value = true;
    try {
      const saved = await flowApi.saveDraft(selectedFlow.value.flowKey, {
        definitionJson: definition.value
      });
      savedDefinition.value = definition.value;
      outcome.value = '草稿已保存，尚未发布。';
      await refreshVersions(saved);
    } catch (cause) { error.value = errorText(cause); }
    finally { pending.value = false; }
  }

  async function publish() {
    if (!selectedFlow.value || pending.value || loading.value || dirty.value || version.value?.publishStatus !== 'DRAFT') return;
    pending.value = true;
    try {
      if (!await confirmAction('确认发布此草稿版本？数据库发布完成不代表运行时已经激活。', { confirmText: '发布版本' })) return;
      error.value = '';
      const published = await flowApi.publish(selectedFlow.value.flowKey, { version: selectedVersion.value });
      outcome.value = '版本已发布；运行时通知和激活结果尚未确认。';
      await refreshVersions(published);
    } catch (cause) { error.value = errorText(cause); }
    finally { pending.value = false; }
  }

  onMounted(reload);
  return { flows, selectedFlow, versions, selectedVersion, definition, version,
    loading, pending, error, outcome, dirty, reload, selectFlow, selectVersion, saveDraft, publish };
}
