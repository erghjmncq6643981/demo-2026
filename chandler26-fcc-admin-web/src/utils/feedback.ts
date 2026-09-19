/**
 * 全局统一反馈层 (Toast / Confirm)
 *
 * <p>背景：管理端此前散落着大量原生 `window.alert()` / `window.confirm()`。
 * 浏览器原生弹窗会强行带上当前站点来源（例如 "10.211.65.2:8000 says ..."），
 * 既阻塞整个页面线程，又与应用自身的圆角卡片设计语言完全割裂，观感上像是
 * "浏览器在替系统说话"，因此统一收敛为应用内的非阻塞提示与品牌化确认框。</p>
 *
 * <p>实现复用已全局注册的 Element Plus (ElMessage / ElMessageBox)，
 * 调用方只需替换函数名，无需引入新的依赖或组件。</p>
 */

import { ElMessage, ElMessageBox } from 'element-plus';

export type FeedbackType = 'success' | 'warning' | 'error' | 'info';

/**
 * 轻量非阻塞提示：右上角浮层，自动消失，不打断用户当前操作。
 *
 * @param message 提示文案
 * @param type    语义类型，决定图标与主色调
 */
export function toast(message: string, type: FeedbackType = 'info'): void {
  ElMessage({
    message,
    type,
    duration: 2800,
    grouping: true,
    showClose: false,
    offset: 88,
    customClass: 'fcc-message',
  });
}

/** 成功提示 */
export const toastSuccess = (message: string): void => toast(message, 'success');

/** 警告提示 */
export const toastWarning = (message: string): void => toast(message, 'warning');

/** 错误提示 */
export const toastError = (message: string): void => toast(message, 'error');

/** 普通信息提示 */
export const toastInfo = (message: string): void => toast(message, 'info');

export interface ConfirmOptions {
  /** 弹窗标题，默认「操作确认」 */
  title?: string;
  /** 确认按钮文案 */
  confirmText?: string;
  /** 取消按钮文案 */
  cancelText?: string;
  /** 是否按危险操作渲染（红色确认按钮），用于删除、销户、强制挂断等不可逆动作 */
  danger?: boolean;
}

/**
 * 品牌化二次确认框，返回 Promise<boolean>：
 * 用户点「确认」得到 true，点「取消」或关闭遮罩得到 false。
 *
 * <p>用法与原生的 `if (confirm(msg))` 几乎一致，只需改为 `if (await confirmAction(msg))`。</p>
 *
 * @param message 确认正文
 * @param options 标题、按钮文案与危险态
 * @return 是否确认执行
 */
export async function confirmAction(message: string, options: ConfirmOptions = {}): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, options.title ?? '操作确认', {
      confirmButtonText: options.confirmText ?? '确认',
      cancelButtonText: options.cancelText ?? '取消',
      type: options.danger ? 'error' : 'warning',
      customClass: options.danger ? 'fcc-confirm fcc-confirm-danger' : 'fcc-confirm',
      draggable: false,
      closeOnClickModal: false,
      autofocus: false,
      distinguishCancelAndClose: false,
    });
    return true;
  } catch {
    // 取消 / 关闭遮罩 / 按 ESC 均走 reject，统一归一化为「不执行」
    return false;
  }
}

/**
 * 从异常对象中提取可直接展示给用户的文案
 *
 * @param err     捕获到的异常
 * @param fallback 兜底文案
 * @return 展示文案
 */
export function errorText(err: unknown, fallback = '网络异常，请稍后重试'): string {
  const message = (err as { message?: string })?.message;
  return message && message.trim() ? message : fallback;
}
