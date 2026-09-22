/**
 * 电信级 WebRTC 语音流与 SIP 信令服务 (基于 JsSIP)
 * <p>
 * 负责与 FreeSWITCH 5066 (ws/wss) 建立 SIP over WebSocket 长连接注册，
 * 并在通话生命周期中接管本地麦克风与远端 RTP 语音流的 WebRTC 协商与播放。
 * </p>
 */

import JsSIP from 'jssip';
import { ref } from 'vue';
import type { SipRuntimeConfig } from '../shared/config/runtimeConfig';

export type SipRegistrationState = 'UNREGISTERED' | 'CONNECTING' | 'REGISTERED' | 'REGISTRATION_FAILED';
export type WebRtcSessionState = 'IDLE' | 'CALLING' | 'RINGING' | 'CONNECTED' | 'TERMINATED';

class SipWebRtcService {
  private ua: JsSIP.UA | null = null;
  private currentSession: any = null;
  private remoteAudioElement: HTMLAudioElement | null = null;
  private runtimeConfig: SipRuntimeConfig | null = null;

  public registrationState = ref<SipRegistrationState>('UNREGISTERED');
  public sessionState = ref<WebRtcSessionState>('IDLE');
  public isRegistered = ref(false);
  public lastError = ref<string | null>(null);

  // 回调事件钩子
  private onIncomingCallCallback?: (session: any, caller: string) => void;
  private onCallConnectedCallback?: () => void;
  private onCallEndedCallback?: (cause: string) => void;

  /**
   * 确保页面中存在用于播放远程对方声音的全局 <audio> 元素
   */
  private ensureAudioElement(): HTMLAudioElement {
    if (!this.remoteAudioElement) {
      let el = document.getElementById('fcc-remote-audio-sink') as HTMLAudioElement;
      if (!el) {
        el = document.createElement('audio');
        el.id = 'fcc-remote-audio-sink';
        el.autoplay = true;
        el.style.display = 'none';
        document.body.appendChild(el);
      }
      this.remoteAudioElement = el;
    }
    return this.remoteAudioElement;
  }

  /**
   * 注册与初始化 WebRTC SIP 客户端
   */
  public init(extension: string, runtimeConfig: SipRuntimeConfig | null) {
    if (this.ua) {
      this.destroy();
    }

    if (!runtimeConfig) {
      this.registrationState.value = 'REGISTRATION_FAILED';
      this.isRegistered.value = false;
      this.lastError.value = 'SIP 运行时配置不完整，软话机未启动';
      return;
    }

    this.runtimeConfig = runtimeConfig;
    const sipUri = `sip:${extension}@${runtimeConfig.domain}`;

    console.log(`[WebRTC SIP] 正在初始化 WebRTC SIP 客户端: ${sipUri}, WS: ${runtimeConfig.wsUrl}`);
    this.registrationState.value = 'CONNECTING';

    try {
      const socket = new JsSIP.WebSocketInterface(runtimeConfig.wsUrl);

      const configuration = {
        sockets: [socket],
        uri: sipUri,
        password: runtimeConfig.password,
        register: true,
        session_timers: false,
        user_agent: 'FCC-Agent-WebRTC/2.0'
      };

      this.ua = new JsSIP.UA(configuration);

      // SIP 传输与注册事件
      this.ua.on('connected', () => {
        console.log('✅ [WebRTC SIP] WebSocket 传输通道连接成功');
      });

      this.ua.on('disconnected', () => {
        console.warn('⚠️ [WebRTC SIP] WebSocket 传输通道断开');
        this.registrationState.value = 'UNREGISTERED';
        this.isRegistered.value = false;
      });

      this.ua.on('registered', () => {
        console.log(`🎉 [WebRTC SIP] 分机 ${extension} 在软交换注册成功 (200 OK)`);
        this.registrationState.value = 'REGISTERED';
        this.isRegistered.value = true;
        this.lastError.value = null;
      });

      this.ua.on('unregistered', () => {
        console.log(`[WebRTC SIP] 分机 ${extension} 已注销`);
        this.registrationState.value = 'UNREGISTERED';
        this.isRegistered.value = false;
      });

      this.ua.on('registrationFailed', (e: any) => {
        const cause = e?.cause || 'Unknown';
        console.error(`❌ [WebRTC SIP] 分机 ${extension} 注册失败:`, cause);
        this.registrationState.value = 'REGISTRATION_FAILED';
        this.isRegistered.value = false;
        this.lastError.value = `注册失败: ${cause}`;
      });

      // 通话 Session 监听 (来电与外呼)
      this.ua.on('newRTCSession', (data: any) => {
        const session = data.session;
        this.handleNewSession(session);
      });

      this.ua.start();
    } catch (err: any) {
      console.error('[WebRTC SIP] 初始化失败，请核对本人终端配置');
      this.registrationState.value = 'REGISTRATION_FAILED';
      this.lastError.value = 'SIP 初始化失败，请核对本人终端配置';
    }
  }

  /**
   * 处理新建 RTC 通话 Session (来电或去电)
   */
  private handleNewSession(session: any) {
    this.currentSession = session;

    if (session.direction === 'incoming') {
      console.log('📞 [WebRTC SIP] 收到远程来电 INVITE:', session.remote_identity.uri.toString());
      this.sessionState.value = 'RINGING';
      const caller = session.remote_identity.uri.user || '外部来电';
      if (this.onIncomingCallCallback) {
        this.onIncomingCallCallback(session, caller);
      }
    } else {
      console.log('📱 [WebRTC SIP] 发起外呼 Session:', session.remote_identity.uri.toString());
      this.sessionState.value = 'CALLING';
    }

    // 绑定 WebRTC PeerConnection 媒体流事件
    session.on('peerconnection', (e: any) => {
      const pc: RTCPeerConnection = e.peerconnection;
      console.log('🔗 [WebRTC SIP] RTCPeerConnection 建立就绪');

      pc.addEventListener('track', (trackEvent: RTCTrackEvent) => {
        console.log('🎵 [WebRTC SIP] 捕获远端音频媒体流 Track:', trackEvent.track.kind);
        const [remoteStream] = trackEvent.streams;
        if (remoteStream) {
          const audioEl = this.ensureAudioElement();
          audioEl.srcObject = remoteStream;
          audioEl.play().catch(pErr => {
            console.warn('[WebRTC SIP] 自动播放远端音频被浏览器策略拦截，等待用户交互:', pErr);
          });
        }
      });
    });

    session.on('connecting', () => {
      console.log('[WebRTC SIP] 正在进行 SDP 握手协商...');
    });

    session.on('progress', () => {
      console.log('[WebRTC SIP] 对方话道振铃中 (180/183 Session Progress)');
    });

    session.on('confirmed', () => {
      console.log('🎙️ [WebRTC SIP] 双方通话接通成功 (200 OK ACK), RTP 媒体通道开始双向送音');
      this.sessionState.value = 'CONNECTED';
      if (this.onCallConnectedCallback) {
        this.onCallConnectedCallback();
      }
    });

    session.on('ended', (e: any) => {
      console.log('📴 [WebRTC SIP] 通话正常结束 (BYE):', e?.cause);
      this.cleanupSession();
      if (this.onCallEndedCallback) {
        this.onCallEndedCallback(e?.cause || 'BYE');
      }
    });

    session.on('failed', (e: any) => {
      console.warn('❌ [WebRTC SIP] 通话失败或拒接:', e?.cause);
      this.cleanupSession();
      if (this.onCallEndedCallback) {
        this.onCallEndedCallback(e?.cause || 'FAILED');
      }
    });
  }

  /**
   * 软话机接听来电
   */
  public answer(): boolean {
    if (!this.currentSession || this.currentSession.direction !== 'incoming') {
      console.warn('[WebRTC SIP] 当前没有可接听的来电 Session');
      return false;
    }

    try {
      this.currentSession.answer({
        mediaConstraints: { audio: true, video: false },
        pcConfig: {
          iceServers: this.runtimeConfig?.iceServers ?? []
        }
      });
      return true;
    } catch (e) {
      console.error('[WebRTC SIP] 接听失败:', e);
      return false;
    }
  }

  /**
   * 软话机挂断通话
   */
  public hangup(): boolean {
    if (!this.currentSession) {
      return false;
    }
    try {
      this.currentSession.terminate();
      this.cleanupSession();
      return true;
    } catch (e) {
      console.warn('[WebRTC SIP] 挂断异常:', e);
      this.cleanupSession();
      return false;
    }
  }

  /**
   * 发送二次 DTMF 按键
   */
  public sendDtmf(digit: string) {
    if (this.currentSession && this.sessionState.value === 'CONNECTED') {
      try {
        this.currentSession.sendDTMF(digit);
      } catch (e) {
        console.warn('[WebRTC SIP] 发送 DTMF 失败:', e);
      }
    }
  }

  /**
   * 静音/解除静音麦克风
   */
  public toggleMute(mute: boolean) {
    if (this.currentSession) {
      try {
        if (mute) {
          this.currentSession.mute({ audio: true });
        } else {
          this.currentSession.unmute({ audio: true });
        }
      } catch (e) {
        console.warn('[WebRTC SIP] 切换静音失败:', e);
      }
    }
  }

  private cleanupSession() {
    this.sessionState.value = 'IDLE';
    this.currentSession = null;
    if (this.remoteAudioElement) {
      this.remoteAudioElement.srcObject = null;
    }
  }

  /**
   * 注册事件监听钩子
   */
  public onIncomingCall(cb: (session: any, caller: string) => void) {
    this.onIncomingCallCallback = cb;
  }

  public onCallConnected(cb: () => void) {
    this.onCallConnectedCallback = cb;
  }

  public onCallEnded(cb: (cause: string) => void) {
    this.onCallEndedCallback = cb;
  }

  /**
   * 销毁并注销
   */
  public destroy() {
    if (this.currentSession) {
      try { this.currentSession.terminate(); } catch {}
      this.currentSession = null;
    }
    if (this.ua) {
      try {
        this.ua.stop();
      } catch {}
      this.ua = null;
    }
    this.registrationState.value = 'UNREGISTERED';
    this.isRegistered.value = false;
    this.sessionState.value = 'IDLE';
    this.runtimeConfig = null;
  }
}

export const sipWebRtcService = new SipWebRtcService();
