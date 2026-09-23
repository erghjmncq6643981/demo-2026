export type SipSessionDirection = 'incoming' | 'outgoing';
export type SipSessionPhase = 'NEW' | 'RINGING' | 'CONNECTING' | 'CONNECTED';

export interface SipSessionBinding {
  sessionId: string;
  direction: SipSessionDirection;
  phase: SipSessionPhase;
  callId: string | null;
}

export interface RegisterSessionResult {
  accepted: boolean;
  isNew: boolean;
  binding: SipSessionBinding | null;
}

/**
 * Maintains the one-active-media-session invariant and its association with an
 * authoritative FCC business call. SIP session IDs never replace business call IDs.
 */
export class SessionCallRegistry {
  private readonly bindings = new Map<string, SipSessionBinding>();
  private activeSessionId: string | null = null;
  private pendingCallId: string | null = null;

  register(sessionId: string, direction: SipSessionDirection): RegisterSessionResult {
    const existing = this.bindings.get(sessionId);
    if (existing) {
      return { accepted: true, isNew: false, binding: { ...existing } };
    }
    if (this.activeSessionId) {
      return { accepted: false, isNew: false, binding: null };
    }

    const binding: SipSessionBinding = {
      sessionId,
      direction,
      phase: direction === 'incoming' ? 'RINGING' : 'NEW',
      callId: this.pendingCallId,
    };
    this.pendingCallId = null;
    this.activeSessionId = sessionId;
    this.bindings.set(sessionId, binding);
    return { accepted: true, isNew: true, binding: { ...binding } };
  }

  bindBusinessCall(callId: string): SipSessionBinding | null {
    if (!this.activeSessionId) {
      this.pendingCallId = callId;
      return null;
    }

    const binding = this.bindings.get(this.activeSessionId);
    if (!binding) return null;
    if (binding.callId && binding.callId !== callId) return null;
    binding.callId = callId;
    this.pendingCallId = null;
    return { ...binding };
  }

  releasePendingBusinessCall(callId: string): void {
    if (this.pendingCallId === callId) this.pendingCallId = null;
  }

  markPhase(sessionId: string, phase: SipSessionPhase): SipSessionBinding | null {
    const binding = this.bindings.get(sessionId);
    if (!binding) return null;
    binding.phase = phase;
    return { ...binding };
  }

  get(sessionId: string): SipSessionBinding | null {
    const binding = this.bindings.get(sessionId);
    return binding ? { ...binding } : null;
  }

  getActive(): SipSessionBinding | null {
    return this.activeSessionId ? this.get(this.activeSessionId) : null;
  }

  finish(sessionId: string): { wasActive: boolean; binding: SipSessionBinding | null } {
    const binding = this.bindings.get(sessionId);
    if (!binding) return { wasActive: false, binding: null };

    const wasActive = this.activeSessionId === sessionId;
    this.bindings.delete(sessionId);
    if (wasActive) this.activeSessionId = null;
    return { wasActive, binding: { ...binding } };
  }

  reset(): void {
    this.bindings.clear();
    this.activeSessionId = null;
    this.pendingCallId = null;
  }
}
