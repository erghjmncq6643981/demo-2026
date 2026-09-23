import { ref } from 'vue';

export interface MicrophoneCheckResult {
  ok: boolean;
  message: string;
  failureType?: 'PERMISSION_DENIED' | 'UNAVAILABLE' | 'CANCELLED';
}

/** Owns browser audio permissions, device selection, and remote playback. */
export class BrowserAudioManager {
  private remoteAudioElement: HTMLAudioElement | null = null;
  private remoteStream: MediaStream | null = null;
  private permissionGeneration = 0;
  private deviceChangeHandler: (() => void) | null = null;
  private onPlaybackProblem: (message: string) => void = () => undefined;

  public autoplayBlocked = ref(false);
  public microphonePermissionGranted = ref(false);
  public audioInputDevices = ref<MediaDeviceInfo[]>([]);
  public audioOutputDevices = ref<MediaDeviceInfo[]>([]);
  public selectedInputDeviceId = ref('');
  public selectedOutputDeviceId = ref('');
  public audioInputLabel = ref('未检测麦克风');
  public audioOutputLabel = ref('未检测扬声器');
  public outputSelectionSupported = ref(false);

  start(onPlaybackProblem: (message: string) => void): void {
    this.onPlaybackProblem = onPlaybackProblem;
    if (!navigator.mediaDevices || this.deviceChangeHandler) return;
    this.deviceChangeHandler = () => {
      void this.refreshDevices();
    };
    navigator.mediaDevices.addEventListener('devicechange', this.deviceChangeHandler);
  }

  stop(): void {
    this.permissionGeneration += 1;
    if (navigator.mediaDevices && this.deviceChangeHandler) {
      navigator.mediaDevices.removeEventListener('devicechange', this.deviceChangeHandler);
    }
    this.deviceChangeHandler = null;
    this.clearRemoteStream();
    this.microphonePermissionGranted.value = false;
  }

  async checkMicrophonePermission(): Promise<MicrophoneCheckResult> {
    const generation = ++this.permissionGeneration;
    if (!navigator.mediaDevices?.getUserMedia) {
      this.microphonePermissionGranted.value = false;
      return {
        ok: false,
        message: '当前环境不支持 WebRTC 音频设备',
        failureType: 'UNAVAILABLE',
      };
    }

    let probeStream: MediaStream | null = null;
    try {
      probeStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      probeStream.getTracks().forEach(track => track.stop());
      probeStream = null;
      if (generation !== this.permissionGeneration) {
        return { ok: false, message: '麦克风检查已取消', failureType: 'CANCELLED' };
      }
      await this.refreshDevices();
      this.microphonePermissionGranted.value = true;
      return { ok: true, message: '麦克风可用' };
    } catch (error) {
      this.microphonePermissionGranted.value = false;
      const message = error instanceof DOMException && error.name === 'NotAllowedError'
        ? '麦克风权限被拒绝，请在系统设置中允许访问'
        : '无法访问麦克风，请检查设备占用与系统权限';
      return {
        ok: false,
        message,
        failureType: error instanceof DOMException && error.name === 'NotAllowedError'
          ? 'PERMISSION_DENIED'
          : 'UNAVAILABLE',
      };
    } finally {
      probeStream?.getTracks().forEach(track => track.stop());
    }
  }

  async refreshDevices(): Promise<void> {
    if (!navigator.mediaDevices?.enumerateDevices) return;
    const devices = await navigator.mediaDevices.enumerateDevices();
    this.audioInputDevices.value = devices.filter(device => device.kind === 'audioinput');
    this.audioOutputDevices.value = devices.filter(device => device.kind === 'audiooutput');
    this.outputSelectionSupported.value = Boolean(this.getSetSinkId(this.ensureAudioElement()));

    this.selectedInputDeviceId.value = this.resolveSelectedDevice(
      this.audioInputDevices.value,
      this.selectedInputDeviceId.value,
    );
    this.selectedOutputDeviceId.value = this.resolveSelectedDevice(
      this.audioOutputDevices.value,
      this.selectedOutputDeviceId.value,
    );
    this.updateDeviceLabels();
    await this.applyOutputDevice();
  }

  selectInputDevice(deviceId: string): void {
    if (!this.audioInputDevices.value.some(device => device.deviceId === deviceId)) return;
    this.selectedInputDeviceId.value = deviceId;
    this.updateDeviceLabels();
  }

  async selectOutputDevice(deviceId: string): Promise<boolean> {
    if (!this.audioOutputDevices.value.some(device => device.deviceId === deviceId)) return false;
    this.selectedOutputDeviceId.value = deviceId;
    this.updateDeviceLabels();
    return this.applyOutputDevice();
  }

  async attachRemoteStream(stream: MediaStream): Promise<void> {
    this.remoteStream = stream;
    const audioElement = this.ensureAudioElement();
    audioElement.srcObject = stream;
    await this.applyOutputDevice();
    try {
      await audioElement.play();
      this.autoplayBlocked.value = false;
    } catch {
      this.autoplayBlocked.value = true;
      this.onPlaybackProblem('浏览器阻止了远端音频自动播放');
    }
  }

  async resumeRemoteAudio(): Promise<boolean> {
    if (!this.remoteAudioElement?.srcObject) return false;
    try {
      await this.remoteAudioElement.play();
      this.autoplayBlocked.value = false;
      return true;
    } catch {
      this.autoplayBlocked.value = true;
      return false;
    }
  }

  clearRemoteStream(): void {
    this.remoteStream?.getTracks().forEach(track => track.stop());
    this.remoteStream = null;
    if (this.remoteAudioElement) {
      this.remoteAudioElement.pause();
      this.remoteAudioElement.srcObject = null;
    }
    this.autoplayBlocked.value = false;
  }

  private ensureAudioElement(): HTMLAudioElement {
    if (!this.remoteAudioElement) {
      let element = document.getElementById('fcc-remote-audio-sink') as HTMLAudioElement | null;
      if (!element) {
        element = document.createElement('audio');
        element.id = 'fcc-remote-audio-sink';
        element.autoplay = true;
        element.style.display = 'none';
        document.body.appendChild(element);
      }
      this.remoteAudioElement = element;
    }
    return this.remoteAudioElement;
  }

  private resolveSelectedDevice(devices: MediaDeviceInfo[], currentDeviceId: string): string {
    if (devices.some(device => device.deviceId === currentDeviceId)) return currentDeviceId;
    return devices.find(device => device.deviceId === 'default')?.deviceId
      || devices[0]?.deviceId
      || '';
  }

  private updateDeviceLabels(): void {
    const input = this.audioInputDevices.value.find(
      device => device.deviceId === this.selectedInputDeviceId.value,
    );
    const output = this.audioOutputDevices.value.find(
      device => device.deviceId === this.selectedOutputDeviceId.value,
    );
    this.audioInputLabel.value = input?.label || (input ? '默认麦克风' : '未检测麦克风');
    this.audioOutputLabel.value = output?.label || (output ? '默认扬声器' : '未检测扬声器');
  }

  private async applyOutputDevice(): Promise<boolean> {
    const setSinkId = this.getSetSinkId(this.ensureAudioElement());
    if (!setSinkId || !this.selectedOutputDeviceId.value) return false;
    try {
      await setSinkId(this.selectedOutputDeviceId.value);
      return true;
    } catch {
      this.onPlaybackProblem('无法切换扬声器，请检查系统音频权限');
      return false;
    }
  }

  private getSetSinkId(audioElement: HTMLAudioElement): ((sinkId: string) => Promise<void>) | null {
    const candidate = (audioElement as unknown as {
      setSinkId?: (sinkId: string) => Promise<void>;
    }).setSinkId;
    return typeof candidate === 'function' ? candidate.bind(audioElement) : null;
  }
}
