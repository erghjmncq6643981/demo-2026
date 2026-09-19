/**
 * 电信级电话音频合成器 (Web Audio API 双频振铃 + DTMF 拨号按键音纯算法合成)
 */

const DTMF_FREQS: Record<string, [number, number]> = {
  '1': [697, 1209],
  '2': [697, 1336],
  '3': [697, 1477],
  '4': [770, 1209],
  '5': [770, 1336],
  '6': [770, 1477],
  '7': [852, 1209],
  '8': [852, 1336],
  '9': [852, 1477],
  '*': [941, 1209],
  '0': [941, 1336],
  '#': [941, 1477],
};

class AudioService {
  private audioCtx: AudioContext | null = null;
  private osc1: OscillatorNode | null = null;
  private osc2: OscillatorNode | null = null;
  private gainNode: GainNode | null = null;
  private isRinging: boolean = false;
  private cadenceInterval: number | null = null;
  public isMuted: boolean = false;

  private initContext() {
    if (!this.audioCtx) {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.audioCtx = new AudioContextClass();
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
  }

  public startRingtone() {
    if (this.isRinging || this.isMuted) return;
    try {
      this.initContext();
      if (!this.audioCtx) return;

      this.isRinging = true;

      const playBurst = () => {
        if (!this.isRinging || !this.audioCtx) return;

        this.osc1 = this.audioCtx.createOscillator();
        this.osc2 = this.audioCtx.createOscillator();
        this.gainNode = this.audioCtx.createGain();

        this.osc1.type = 'sine';
        this.osc1.frequency.setValueAtTime(440, this.audioCtx.currentTime); // 440Hz
        this.osc2.type = 'sine';
        this.osc2.frequency.setValueAtTime(480, this.audioCtx.currentTime); // 480Hz

        this.gainNode.gain.setValueAtTime(0.12, this.audioCtx.currentTime);

        this.osc1.connect(this.gainNode);
        this.osc2.connect(this.gainNode);
        this.gainNode.connect(this.audioCtx.destination);

        this.osc1.start();
        this.osc2.start();

        // 响铃 1 秒后渐隐静音
        this.gainNode.gain.exponentialRampToValueAtTime(0.0001, this.audioCtx.currentTime + 1.0);
        setTimeout(() => {
          this.stopBurst();
        }, 1000);
      };

      playBurst();
      // 循环节律: 1秒响铃 + 2秒停顿 = 3秒一个周期
      this.cadenceInterval = window.setInterval(() => {
        playBurst();
      }, 3000);
    } catch (e) {
      console.warn('Web Audio synthesis failed, skipping ring audio:', e);
    }
  }

  private stopBurst() {
    try {
      if (this.osc1) {
        this.osc1.stop();
        this.osc1.disconnect();
        this.osc1 = null;
      }
      if (this.osc2) {
        this.osc2.stop();
        this.osc2.disconnect();
        this.osc2 = null;
      }
    } catch {
      // ignore
    }
  }

  public stopRingtone() {
    this.isRinging = false;
    if (this.cadenceInterval !== null) {
      clearInterval(this.cadenceInterval);
      this.cadenceInterval = null;
    }
    this.stopBurst();
  }

  public playDtmf(key: string) {
    const freqs = DTMF_FREQS[key];
    if (!freqs) return;
    try {
      this.initContext();
      if (!this.audioCtx) return;

      const now = this.audioCtx.currentTime;
      const oscLow = this.audioCtx.createOscillator();
      const oscHigh = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();

      oscLow.type = 'sine';
      oscLow.frequency.setValueAtTime(freqs[0], now);
      oscHigh.type = 'sine';
      oscHigh.frequency.setValueAtTime(freqs[1], now);

      gain.gain.setValueAtTime(0.08, now);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.12);

      oscLow.connect(gain);
      oscHigh.connect(gain);
      gain.connect(this.audioCtx.destination);

      oscLow.start(now);
      oscHigh.start(now);
      oscLow.stop(now + 0.12);
      oscHigh.stop(now + 0.12);
    } catch (e) {
      // ignore audio play issues
    }
  }

  public toggleMute(): boolean {
    this.isMuted = !this.isMuted;
    if (this.isMuted && this.isRinging) {
      this.stopRingtone();
    }
    return this.isMuted;
  }
}

export const audioService = new AudioService();
