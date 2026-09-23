import { computed, ref } from 'vue';

export interface MicrophoneCheckResult {
  ok: boolean;
  message: string;
  failureType?: 'CANCELLED' | 'PERMISSION_DENIED' | 'FAILED';
}

/**
 * Manages browser audio input/output devices, microphone permissions,
 * and remote WebRTC audio playback.
 */
export class BrowserAudioManager {
  public readonly audioInputDevices = ref<MediaDeviceInfo[]>([]);
  public readonly audioOutputDevices = ref<MediaDeviceInfo[]>([]);
  public readonly selectedInputDeviceId = ref<string>('');
  public readonly selectedOutputDeviceId = ref<string>('');
  public readonly microphonePermissionGranted = ref<boolean>(false);
  public readonly autoplayBlocked = ref<boolean>(false);
  public readonly outputSelectionSupported = ref<boolean>(
    typeof HTMLAudioElement !== 'undefined' && 'setSinkId' in HTMLAudioElement.prototype
  );

  private audioElement: HTMLAudioElement | null = null;
  private messageCallback: ((message: string) => void) | null = null;
  private deviceChangeListener: (() => void) | null = null;

  public readonly audioInputLabel = computed<string>(() => {
    if (!this.selectedInputDeviceId.value) {
      return this.audioInputDevices.value[0]?.label || '默认麦克风';
    }
    const found = this.audioInputDevices.value.find(
      d => d.deviceId === this.selectedInputDeviceId.value
    );
    return found?.label || '默认麦克风';
  });

  public readonly audioOutputLabel = computed<string>(() => {
    if (!this.outputSelectionSupported.value) {
      return '系统默认扬声器';
    }
    if (!this.selectedOutputDeviceId.value) {
      return this.audioOutputDevices.value[0]?.label || '默认扬声器';
    }
    const found = this.audioOutputDevices.value.find(
      d => d.deviceId === this.selectedOutputDeviceId.value
    );
    return found?.label || '默认扬声器';
  });

  public start(onMessage?: (message: string) => void): void {
    this.messageCallback = onMessage ?? null;
    this.ensureAudioElement();

    if (typeof navigator !== 'undefined' && navigator.mediaDevices) {
      void this.refreshDevices();

      if (!this.deviceChangeListener && typeof navigator.mediaDevices.addEventListener === 'function') {
        this.deviceChangeListener = () => {
          void this.refreshDevices();
        };
        navigator.mediaDevices.addEventListener('devicechange', this.deviceChangeListener);
      }
    }
  }

  public stop(): void {
    this.clearRemoteStream();
    if (
      this.deviceChangeListener &&
      typeof navigator !== 'undefined' &&
      navigator.mediaDevices &&
      typeof navigator.mediaDevices.removeEventListener === 'function'
    ) {
      navigator.mediaDevices.removeEventListener('devicechange', this.deviceChangeListener);
      this.deviceChangeListener = null;
    }
    this.messageCallback = null;
  }

  public async refreshDevices(): Promise<void> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.enumerateDevices) {
      return;
    }

    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      this.audioInputDevices.value = devices.filter(d => d.kind === 'audioinput');
      this.audioOutputDevices.value = devices.filter(d => d.kind === 'audiooutput');

      if (
        this.selectedInputDeviceId.value &&
        !this.audioInputDevices.value.some(d => d.deviceId === this.selectedInputDeviceId.value)
      ) {
        this.selectedInputDeviceId.value = '';
      }

      if (
        this.selectedOutputDeviceId.value &&
        !this.audioOutputDevices.value.some(d => d.deviceId === this.selectedOutputDeviceId.value)
      ) {
        this.selectedOutputDeviceId.value = '';
      }
    } catch (err) {
      console.warn('[BrowserAudioManager] 无法枚举音频设备:', err);
    }
  }

  public async checkMicrophonePermission(): Promise<MicrophoneCheckResult> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      return {
        ok: false,
        message: '当前浏览器环境不支持获取麦克风',
        failureType: 'FAILED',
      };
    }

    try {
      const constraints: MediaStreamConstraints = {
        audio: this.selectedInputDeviceId.value
          ? { deviceId: { exact: this.selectedInputDeviceId.value } }
          : true,
        video: false,
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      stream.getTracks().forEach(track => track.stop());

      this.microphonePermissionGranted.value = true;
      await this.refreshDevices();

      return {
        ok: true,
        message: '麦克风权限正常，设备就绪',
      };
    } catch (error: any) {
      this.microphonePermissionGranted.value = false;
      const errorName = String(error?.name || '');

      if (errorName === 'NotAllowedError' || errorName === 'PermissionDeniedError') {
        return {
          ok: false,
          message: '麦克风权限已被拒绝，请在浏览器地址栏允许麦克风权限后重试',
          failureType: 'PERMISSION_DENIED',
        };
      }

      if (errorName === 'AbortError') {
        return {
          ok: false,
          message: '麦克风权限请求已取消',
          failureType: 'CANCELLED',
        };
      }

      if (errorName === 'NotFoundError' || errorName === 'DevicesNotFoundError') {
        return {
          ok: false,
          message: '未检测到可用的麦克风硬件设备',
          failureType: 'FAILED',
        };
      }

      return {
        ok: false,
        message: `麦克风不可用: ${error?.message || '未知异常'}`,
        failureType: 'FAILED',
      };
    }
  }

  public async attachRemoteStream(stream: MediaStream): Promise<void> {
    const audioEl = this.ensureAudioElement();
    if (!audioEl) return;

    audioEl.srcObject = stream;
    try {
      await audioEl.play();
      this.autoplayBlocked.value = false;
    } catch (err: any) {
      console.warn('[BrowserAudioManager] 远端音频自动播放受限:', err);
      this.autoplayBlocked.value = true;
      this.messageCallback?.('浏览器自动播放受阻，请点击“恢复远端声音”');
    }
  }

  public clearRemoteStream(): void {
    if (this.audioElement) {
      this.audioElement.srcObject = null;
      try {
        this.audioElement.pause();
      } catch {
        // ignore
      }
    }
    this.autoplayBlocked.value = false;
  }

  public async resumeRemoteAudio(): Promise<void> {
    if (!this.audioElement || !this.audioElement.srcObject) return;

    try {
      await this.audioElement.play();
      this.autoplayBlocked.value = false;
      this.messageCallback?.('远端音频已恢复播放');
    } catch (err) {
      console.warn('[BrowserAudioManager] 无法恢复音频播放:', err);
    }
  }

  public selectInputDevice(deviceId: string): void {
    this.selectedInputDeviceId.value = deviceId;
  }

  public async selectOutputDevice(deviceId: string): Promise<void> {
    this.selectedOutputDeviceId.value = deviceId;
    if (
      this.audioElement &&
      this.outputSelectionSupported.value &&
      typeof (this.audioElement as any).setSinkId === 'function'
    ) {
      try {
        await (this.audioElement as any).setSinkId(deviceId);
      } catch (err) {
        console.warn('[BrowserAudioManager] 设置扬声器输出设备失败:', err);
      }
    }
  }

  private ensureAudioElement(): HTMLAudioElement | null {
    if (!this.audioElement && typeof document !== 'undefined') {
      const el = document.createElement('audio');
      el.autoplay = true;
      el.setAttribute('playsinline', 'true');
      el.style.display = 'none';
      document.body.appendChild(el);
      this.audioElement = el;

      if (
        this.outputSelectionSupported.value &&
        this.selectedOutputDeviceId.value &&
        typeof (el as any).setSinkId === 'function'
      ) {
        void (el as any).setSinkId(this.selectedOutputDeviceId.value);
      }
    }
    return this.audioElement;
  }
}
