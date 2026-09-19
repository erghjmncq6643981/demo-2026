/**
 * 坐席端全局统一反馈层 (Toast / Confirm)
 *
 * <p>坐席端 (chandler26-fcc-client-web) 未引入 UI 组件库，因此这里用
 * Vue 3 的响应式状态 + 一个挂在 App.vue 根节点的 FeedbackHost 组件
 * 自建轻量反馈，替代浏览器原生 `alert` / `confirm`。</p>
 *
 * <p>用法：</p>
 * <pre>
 *   import { toast, confirmAction } from '../utils/feedback';
 *   toast('操作成功', 'success');
 *   if (await confirmAction('确认登出吗？')) { ... }
 * </pre>
 */

import { reactive } from 'vue';

export type FeedbackType = 'success' | 'warning' | 'error' | 'info';

interface ToastItem {
  id: number;
  message: string;
  type: FeedbackType;
}

interface ConfirmState {
  visible: boolean;
  title: string;
  message: string;
  confirmText: string;
  cancelText: string;
  danger: boolean;
  resolve: ((value: boolean) => void) | null;
}

/** 当前展示中的 toast 列表（由 FeedbackHost 消费并渲染） */
export const feedbackState = reactive<{
  toasts: ToastItem[];
  confirm: ConfirmState;
}>({
  toasts: [],
  confirm: {
    visible: false,
    title: '操作确认',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    danger: false,
    resolve: null,
  },
});

let toastSeq = 0;

/** 轻量非阻塞提示，自动消失 */
export function toast(message: string, type: FeedbackType = 'info', duration = 2800): void {
  const id = ++toastSeq;
  feedbackState.toasts.push({ id, message, type });
  window.setTimeout(() => {
    const idx = feedbackState.toasts.findIndex((t) => t.id === id);
    if (idx !== -1) feedbackState.toasts.splice(idx, 1);
  }, duration);
}

export const toastSuccess = (message: string): void => toast(message, 'success');
export const toastWarning = (message: string): void => toast(message, 'warning');
export const toastError = (message: string): void => toast(message, 'error');
export const toastInfo = (message: string): void => toast(message, 'info');

export interface ConfirmOptions {
  title?: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

/** 品牌化二次确认框，返回 Promise<boolean> */
export function confirmAction(message: string, options: ConfirmOptions = {}): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    feedbackState.confirm.title = options.title ?? '操作确认';
    feedbackState.confirm.message = message;
    feedbackState.confirm.confirmText = options.confirmText ?? '确认';
    feedbackState.confirm.cancelText = options.cancelText ?? '取消';
    feedbackState.confirm.danger = options.danger ?? false;
    feedbackState.confirm.resolve = resolve;
    feedbackState.confirm.visible = true;
  });
}

/** 确认框内部调用：返回结果并关闭 */
export function resolveConfirm(value: boolean): void {
  const resolver = feedbackState.confirm.resolve;
  feedbackState.confirm.visible = false;
  feedbackState.confirm.resolve = null;
  if (resolver) resolver(value);
}

/** 从异常对象中提取可展示文案 */
export function errorText(err: unknown, fallback = '网络异常，请稍后重试'): string {
  const message = (err as { message?: string })?.message;
  return message && message.trim() ? message : fallback;
}
