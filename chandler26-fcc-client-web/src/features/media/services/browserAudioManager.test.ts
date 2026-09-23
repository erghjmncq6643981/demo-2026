import { afterEach, describe, expect, it, vi } from 'vitest';
import { BrowserAudioManager } from './browserAudioManager';

function installAudioEnvironment(options?: { permissionDenied?: boolean; autoplayBlocked?: boolean }) {
  const stop = vi.fn();
  const audioElement = {
    srcObject: null as MediaStream | null,
    autoplay: false,
    id: '',
    style: { display: '' },
    pause: vi.fn(),
    play: options?.autoplayBlocked
      ? vi.fn().mockRejectedValue(new Error('blocked'))
      : vi.fn().mockResolvedValue(undefined),
    setSinkId: vi.fn().mockResolvedValue(undefined),
  };
  const enumerateDevices = vi.fn(async () => {
    expect(stop).toHaveBeenCalledOnce();
    return [
      { kind: 'audioinput', deviceId: 'mic-1', label: '测试麦克风' },
      { kind: 'audiooutput', deviceId: 'speaker-1', label: '测试扬声器' },
    ] as MediaDeviceInfo[];
  });

  vi.stubGlobal('document', {
    getElementById: vi.fn(() => null),
    createElement: vi.fn(() => audioElement),
    body: { appendChild: vi.fn() },
  });
  vi.stubGlobal('navigator', {
    mediaDevices: {
      getUserMedia: options?.permissionDenied
        ? vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError'))
        : vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] }),
      enumerateDevices,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    },
  });
  return { audioElement, stop };
}

describe('browser audio manager', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('releases the microphone probe before enumerating devices', async () => {
    const { stop } = installAudioEnvironment();
    const manager = new BrowserAudioManager();

    const result = await manager.checkMicrophonePermission();

    expect(result.ok).toBe(true);
    expect(stop).toHaveBeenCalledOnce();
    expect(manager.audioInputLabel.value).toBe('测试麦克风');
    expect(manager.audioOutputLabel.value).toBe('测试扬声器');
  });

  it('reports denied microphone permission separately', async () => {
    installAudioEnvironment({ permissionDenied: true });
    const manager = new BrowserAudioManager();

    const result = await manager.checkMicrophonePermission();

    expect(result).toEqual({
      ok: false,
      message: '麦克风权限被拒绝，请在系统设置中允许访问',
      failureType: 'PERMISSION_DENIED',
    });
  });

  it('exposes an autoplay recovery state', async () => {
    installAudioEnvironment({ autoplayBlocked: true });
    const manager = new BrowserAudioManager();
    const onProblem = vi.fn();
    manager.start(onProblem);

    await manager.attachRemoteStream({ getTracks: () => [] } as unknown as MediaStream);

    expect(manager.autoplayBlocked.value).toBe(true);
    expect(onProblem).toHaveBeenCalledWith('浏览器阻止了远端音频自动播放');
  });
});
