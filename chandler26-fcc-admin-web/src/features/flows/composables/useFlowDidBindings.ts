import { computed, onMounted, ref, type Ref } from "vue";
import {
  telephonyResourceApi,
  type DidNumberVO,
} from "../../../api/telephonyResourceApi";
import type { FlowDefinitionVO } from "../../../api/flowApi";
import { confirmAction, errorText } from "../../../utils/feedback";

/** Owns the DID entry bindings shown beside one selected inbound flow. */
export function useFlowDidBindings(
  selectedFlow: Ref<FlowDefinitionVO | null>,
) {
  const didNumbers = ref<DidNumberVO[]>([]);
  const loadingDids = ref(false);
  const didPending = ref(false);
  const didError = ref("");
  const bindingDialog = ref(false);
  const selectedDidId = ref("");

  const boundDids = computed(() =>
    didNumbers.value.filter(
      (item) => item.routeKey === selectedFlow.value?.flowKey,
    ),
  );
  const availableDids = computed(() =>
    didNumbers.value.filter(
      (item) =>
        item.status === "ENABLED" &&
        item.routeKey !== selectedFlow.value?.flowKey,
    ),
  );

  async function loadDidNumbers() {
    loadingDids.value = true;
    didError.value = "";
    try {
      didNumbers.value = await telephonyResourceApi.listDids();
    } catch (cause) {
      didError.value = errorText(cause);
    } finally {
      loadingDids.value = false;
    }
  }

  function openBindingDialog() {
    selectedDidId.value = "";
    bindingDialog.value = true;
  }

  async function bindDid() {
    if (!selectedFlow.value || !selectedDidId.value || didPending.value) return;
    const selectedDid = didNumbers.value.find(
      (item) => item.id === selectedDidId.value,
    );
    if (
      selectedDid?.routeKey &&
      !(await confirmAction(
        `号码 ${selectedDid.phoneNumber} 当前属于 ${selectedDid.routeKey}，确认改绑到 ${selectedFlow.value.flowKey}？`,
        { confirmText: "确认改绑" },
      ))
    )
      return;
    didPending.value = true;
    didError.value = "";
    try {
      await telephonyResourceApi.bindDidFlow(
        selectedDidId.value,
        selectedFlow.value.flowKey,
      );
      bindingDialog.value = false;
      await loadDidNumbers();
    } catch (cause) {
      didError.value = errorText(cause);
    } finally {
      didPending.value = false;
    }
  }

  async function unbindDid(did: DidNumberVO) {
    if (
      didPending.value ||
      !(await confirmAction(`确认解除被叫号码 ${did.phoneNumber} 的流程绑定？`))
    )
      return;
    didPending.value = true;
    didError.value = "";
    try {
      await telephonyResourceApi.unbindDidFlow(did.id);
      await loadDidNumbers();
    } catch (cause) {
      didError.value = errorText(cause);
    } finally {
      didPending.value = false;
    }
  }

  onMounted(loadDidNumbers);
  return {
    didNumbers,
    boundDids,
    availableDids,
    loadingDids,
    didPending,
    didError,
    bindingDialog,
    selectedDidId,
    loadDidNumbers,
    openBindingDialog,
    bindDid,
    unbindDid,
  };
}
