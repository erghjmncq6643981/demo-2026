import JsSIP from 'jssip';
import { ref } from 'vue';
import {
  SessionCallRegistry,
  type SipSessionDirection,
} from '../features/media/model/sessionCallRegistry';
import { BrowserAudioManager } from '../features/media/services/browserAudioManager';
import type { SipRuntimeConfig } from '../shared/config/runtimeConfig';

export type SipRegistrationState =
  | 'UNREGISTERED'
  | 'CONNECTING'
  | 'REGISTERED'
  | 'REGISTRATION_FAILED';

export type WebRtcSessionState =
  | 'IDLE'
  | 'CALLING'
  | 'RINGING'
  | 'CONNECTED'
  | 'TERMINATED';

export type WebRtcMediaState =
  | 'IDLE'
  | 'CHECKING_PERMISSION'
  | 'READY'
  | 'PERMISSION_DENIED'
  | 'NEGOTIATING'
  | 'CONNECTED'
  | 'DISCONNECTED'
  | 'FAILED';

export interface SipIncomingCallEvent {
  sessionId: string;
  callId?: string;
  caller: string;
}

export interface SipCallLifecycleEvent {
  sessionId: string;
  callId?: string;
  cause?: string;
}

interface SessionContext {
  session: any;
  peerConnection: RTCPeerConnection | null;
  cleanupCallbacks: Array<() => void>;
  connectedNotifiedCallId: string | null;
}

/**
 * Owns the browser SIP user agent, one active RTC session, and the association
 * between that media session and an FCC business call.
 */
class SipWebRtcService {
  private ua: JsSIP.UA | null = null;
  private readonly registry = new SessionCallRegistry();
  private readonly sessions = new Map<string, SessionContext>();
  private runtimeConfig: SipRuntimeConfig | null = null;
  private sessionSequence = 0;
  public readonly audio = new BrowserAudioManager();

  public registrationState = ref<SipRegistrationState>('UNREGISTERED');
  public sessionState = ref<WebRtcSessionState>('IDLE');
  public mediaState = ref<WebRtcMediaState>('IDLE');
  public isRegistered = ref(false);
  public lastError = ref<string | null>(null);
  public mediaMessage = ref('尚未检查音频设备');
  public incomingCaller = ref('');

  private onIncomingCallCallback?: (event: SipIncomingCallEvent) => void;
  private onCallConnectedCallback?: (event: SipCallLifecycleEvent) => void;
  private onCallEndedCallback?: (event: SipCallLifecycleEvent) => void;

  /** Register and initialize the WebRTC SIP client for the current agent. */
  public init(extension: string, runtimeConfig: SipRuntimeConfig | null): void {
    if (this.ua) this.destroy();

    if (!runtimeConfig) {
      this.registrationState.value = 'REGISTRATION_FAILED';
      this.isRegistered.value = false;
      this.lastError.value = 'SIP 运行时配置不完整，软话机未启动';
      return;
    }

    this.runtimeConfig = runtimeConfig;
    this.registrationState.value = 'CONNECTING';
    this.audio.start(message => {
      this.mediaMessage.value = message;
    });

    try {
      const socket = new JsSIP.WebSocketInterface(runtimeConfig.wsUrl);
      const userAgent = new JsSIP.UA({
        sockets: [socket],
        uri: `sip:${extension}@${runtimeConfig.domain}`,
        password: runtimeConfig.password,
        register: true,
        session_timers: false,
        user_agent: 'FCC-Agent-WebRTC/2.0',
      });
      this.ua = userAgent;

      userAgent.on('disconnected', () => {
        if (this.ua !== userAgent) return;
        this.registrationState.value = 'UNREGISTERED';
        this.isRegistered.value = false;
      });
      userAgent.on('registered', () => {
        if (this.ua !== userAgent) return;
        this.registrationState.value = 'REGISTERED';
        this.isRegistered.value = true;
        this.lastError.value = null;
      });
      userAgent.on('unregistered', () => {
        if (this.ua !== userAgent) return;
        this.registrationState.value = 'UNREGISTERED';
        this.isRegistered.value = false;
      });
      userAgent.on('registrationFailed', (event: any) => {
        if (this.ua !== userAgent) return;
        const cause = String(event?.cause || 'Unknown');
        this.registrationState.value = 'REGISTRATION_FAILED';
        this.isRegistered.value = false;
        this.lastError.value = `注册失败: ${cause}`;
      });
      userAgent.on('newRTCSession', (data: any) => {
        if (this.ua === userAgent) this.handleNewSession(data.session);
      });
      userAgent.start();
      void this.checkMicrophonePermission();
    } catch {
      this.ua = null;
      this.audio.stop();
      this.registrationState.value = 'REGISTRATION_FAILED';
      this.isRegistered.value = false;
      this.lastError.value = 'SIP 初始化失败，请核对本人终端配置';
    }
  }

  /** Associate the active, or next, SIP session with an authoritative call ID. */
  public bindBusinessCall(callId: string): void {
    if (!callId || !this.ua) return;
    const binding = this.registry.bindBusinessCall(callId);
    if (!binding || binding.phase !== 'CONNECTED') return;

    const context = this.sessions.get(binding.sessionId);
    if (context && context.connectedNotifiedCallId !== callId) {
      context.connectedNotifiedCallId = callId;
      this.onCallConnectedCallback?.({ sessionId: binding.sessionId, callId });
    }
  }

  /** Remove a call that ended before its expected SIP session was created. */
  public releasePendingBusinessCall(callId: string): void {
    this.registry.releasePendingBusinessCall(callId);
  }

  /** Request microphone permission and immediately release the probe track. */
  public async checkMicrophonePermission(): Promise<boolean> {
    const activeUserAgent = this.ua;
    if (!activeUserAgent) return false;
    this.mediaState.value = 'CHECKING_PERMISSION';
    this.mediaMessage.value = '正在检查麦克风权限';
    const result = await this.audio.checkMicrophonePermission();
    if (this.ua !== activeUserAgent) return false;
    if (result.failureType === 'CANCELLED') return false;
    this.mediaMessage.value = result.message;
    if (result.ok) {
      if (this.sessionState.value === 'IDLE') this.mediaState.value = 'READY';
      return true;
    }
    this.mediaState.value = result.failureType === 'PERMISSION_DENIED' ? 'PERMISSION_DENIED' : 'FAILED';
    return false;
  }

  /** Answer the active incoming SIP session after validating microphone access. */
  public async answer(): Promise<{ ok: boolean; message?: string }> {
    const context = this.getActiveContext();
    if (!context || context.session.direction !== 'incoming') {
      return { ok: false, message: '当前没有可接听的软电话来电' };
    }
    if (!this.audio.microphonePermissionGranted.value) {
      const allowed = await this.checkMicrophonePermission();
      if (!allowed) return { ok: false, message: this.mediaMessage.value };
    }

    try {
      context.session.answer({
        mediaConstraints: {
          audio: this.audio.selectedInputDeviceId.value
            ? { deviceId: { exact: this.audio.selectedInputDeviceId.value } }
            : true,
          video: false,
        },
        pcConfig: { iceServers: this.runtimeConfig?.iceServers ?? [] },
      });
      return { ok: true };
    } catch {
      return { ok: false, message: '软电话接听失败，请检查麦克风和 SIP 状态' };
    }
  }

  /** Ask the active SIP session to terminate; final state still comes from events. */
  public hangup(): boolean {
    const context = this.getActiveContext();
    if (!context) return false;
    try {
      context.session.terminate();
      return true;
    } catch {
      return false;
    }
  }

  /** Apply mute only when an active connected WebRTC session exists. */
  public toggleMute(mute: boolean): boolean {
    const context = this.getActiveContext();
    if (!context || this.sessionState.value !== 'CONNECTED') return false;
    try {
      if (mute) context.session.mute({ audio: true });
      else context.session.unmute({ audio: true });
      return true;
    } catch {
      return false;
    }
  }

  public onIncomingCall(callback: (event: SipIncomingCallEvent) => void): void {
    this.onIncomingCallCallback = callback;
  }

  public onCallConnected(callback: (event: SipCallLifecycleEvent) => void): void {
    this.onCallConnectedCallback = callback;
  }

  public onCallEnded(callback: (event: SipCallLifecycleEvent) => void): void {
    this.onCallEndedCallback = callback;
  }

  /** Tear down registration, sessions, media tracks, audio elements, and listeners. */
  public destroy(): void {
    for (const [sessionId, context] of Array.from(this.sessions.entries())) {
      this.cleanupSession(sessionId);
      try {
        context.session.terminate();
      } catch {
        // Session may already be terminal.
      }
    }
    if (this.ua) {
      const userAgent = this.ua;
      this.ua = null;
      try {
        userAgent.stop();
      } catch {
        // User agent may already be stopped.
      }
    }
    this.audio.stop();
    this.registry.reset();
    this.registrationState.value = 'UNREGISTERED';
    this.isRegistered.value = false;
    this.sessionState.value = 'IDLE';
    this.mediaState.value = 'IDLE';
    this.mediaMessage.value = '尚未检查音频设备';
    this.runtimeConfig = null;
    this.incomingCaller.value = '';
  }

  private handleNewSession(session: any): void {
    const sessionId = this.resolveSessionId(session);
    const direction: SipSessionDirection = session.direction === 'incoming' ? 'incoming' : 'outgoing';
    const registration = this.registry.register(sessionId, direction);
    if (!registration.accepted) {
      this.rejectBusySession(session);
      return;
    }
    if (!registration.isNew) return;

    const context: SessionContext = {
      session,
      peerConnection: null,
      cleanupCallbacks: [],
      connectedNotifiedCallId: null,
    };
    this.sessions.set(sessionId, context);
    this.mediaState.value = 'NEGOTIATING';
    this.mediaMessage.value = '正在协商通话媒体';

    if (direction === 'incoming') {
      this.sessionState.value = 'RINGING';
      const caller = String(session.remote_identity?.uri?.user || '外部来电');
      this.incomingCaller.value = caller;
      this.onIncomingCallCallback?.({
        sessionId,
        callId: registration.binding?.callId ?? undefined,
        caller,
      });
    } else {
      this.sessionState.value = 'CALLING';
    }

    this.listenSession(context, 'peerconnection', (event: any) => {
      this.attachPeerConnection(sessionId, context, event.peerconnection as RTCPeerConnection);
    });
    this.listenSession(context, 'connecting', () => {
      this.registry.markPhase(sessionId, 'CONNECTING');
      this.mediaState.value = 'NEGOTIATING';
      this.mediaMessage.value = '正在协商通话媒体';
    });
    this.listenSession(context, 'progress', () => {
      const binding = this.registry.get(sessionId);
      this.registry.markPhase(sessionId, binding?.direction === 'incoming' ? 'RINGING' : 'CONNECTING');
    });
    this.listenSession(context, 'confirmed', () => this.handleConfirmed(sessionId, context));
    this.listenSession(context, 'ended', (event: any) => {
      this.handleTerminalSession(sessionId, String(event?.cause || 'BYE'));
    });
    this.listenSession(context, 'failed', (event: any) => {
      this.handleTerminalSession(sessionId, String(event?.cause || 'FAILED'));
    });
  }

  private handleConfirmed(sessionId: string, context: SessionContext): void {
    const binding = this.registry.markPhase(sessionId, 'CONNECTED');
    if (!binding) return;
    this.sessionState.value = 'CONNECTED';
    this.mediaState.value = 'CONNECTED';
    this.mediaMessage.value = '通话媒体已连接';
    if (binding.callId && context.connectedNotifiedCallId !== binding.callId) {
      context.connectedNotifiedCallId = binding.callId;
      this.onCallConnectedCallback?.({ sessionId, callId: binding.callId });
    }
  }

  private handleTerminalSession(sessionId: string, cause: string): void {
    const result = this.registry.finish(sessionId);
    if (!result.binding) return;
    this.cleanupSession(sessionId, result.wasActive);
    this.onCallEndedCallback?.({
      sessionId,
      callId: result.binding.callId ?? undefined,
      cause,
    });
  }

  private attachPeerConnection(
    sessionId: string,
    context: SessionContext,
    peerConnection: RTCPeerConnection,
  ): void {
    context.peerConnection = peerConnection;
    const onTrack = (event: RTCTrackEvent) => {
      if (this.registry.getActive()?.sessionId !== sessionId) return;
      const [stream] = event.streams;
      if (!stream) return;
      void this.audio.attachRemoteStream(stream);
    };
    const onConnectionStateChange = () => {
      if (this.registry.getActive()?.sessionId !== sessionId) return;
      switch (peerConnection.connectionState) {
        case 'connected':
          this.mediaState.value = 'CONNECTED';
          this.mediaMessage.value = '通话媒体已连接';
          break;
        case 'disconnected':
          this.mediaState.value = 'DISCONNECTED';
          this.mediaMessage.value = '通话媒体暂时中断，正在等待恢复';
          break;
        case 'failed':
          this.mediaState.value = 'FAILED';
          this.mediaMessage.value = 'ICE 媒体连接失败';
          break;
        case 'closed':
          if (this.sessionState.value !== 'IDLE') {
            this.mediaState.value = 'DISCONNECTED';
            this.mediaMessage.value = '通话媒体已关闭';
          }
          break;
      }
    };
    const onIceConnectionStateChange = () => {
      if (this.registry.getActive()?.sessionId !== sessionId) return;
      if (peerConnection.iceConnectionState === 'failed') {
        this.mediaState.value = 'FAILED';
        this.mediaMessage.value = 'ICE 协商失败，请检查网络或 TURN 配置';
      }
    };

    peerConnection.addEventListener('track', onTrack);
    peerConnection.addEventListener('connectionstatechange', onConnectionStateChange);
    peerConnection.addEventListener('iceconnectionstatechange', onIceConnectionStateChange);
    context.cleanupCallbacks.push(() => {
      peerConnection.removeEventListener('track', onTrack);
      peerConnection.removeEventListener('connectionstatechange', onConnectionStateChange);
      peerConnection.removeEventListener('iceconnectionstatechange', onIceConnectionStateChange);
    });
  }

  private cleanupSession(sessionId: string, wasActive = true): void {
    const context = this.sessions.get(sessionId);
    if (!context) return;
    context.cleanupCallbacks.splice(0).forEach(cleanup => cleanup());
    context.peerConnection?.getSenders().forEach(sender => sender.track?.stop());
    context.peerConnection?.getReceivers().forEach(receiver => receiver.track?.stop());
    this.sessions.delete(sessionId);

    if (wasActive) {
      this.sessionState.value = 'IDLE';
      this.mediaState.value = this.registrationState.value === 'REGISTERED' ? 'READY' : 'IDLE';
      this.mediaMessage.value = this.registrationState.value === 'REGISTERED'
        ? '麦克风可用'
        : '软电话未注册';
      this.incomingCaller.value = '';
      this.audio.clearRemoteStream();
    }
  }

  private listenSession(
    context: SessionContext,
    eventName: string,
    listener: (...args: any[]) => void,
  ): void {
    context.session.on(eventName, listener);
    context.cleanupCallbacks.push(() => {
      if (typeof context.session.off === 'function') context.session.off(eventName, listener);
      else if (typeof context.session.removeListener === 'function') {
        context.session.removeListener(eventName, listener);
      }
    });
  }

  private rejectBusySession(session: any): void {
    try {
      if (session.direction === 'incoming') {
        session.terminate({ status_code: 486, reason_phrase: 'Busy Here' });
      } else {
        session.terminate();
      }
    } catch {
      // The rejected session may already have become terminal.
    }
  }

  private getActiveContext(): SessionContext | null {
    const active = this.registry.getActive();
    return active ? this.sessions.get(active.sessionId) ?? null : null;
  }

  private resolveSessionId(session: any): string {
    const candidate = session?.id || session?._request?.call_id || session?.request?.call_id;
    if (candidate) return String(candidate);
    this.sessionSequence += 1;
    return `sip-session-${this.sessionSequence}`;
  }
}

export const sipWebRtcService = new SipWebRtcService();
