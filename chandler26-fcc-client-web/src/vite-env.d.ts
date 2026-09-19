/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_FCC_AGENT_WS_URL?: string;
  readonly VITE_FCC_ICE_SERVERS_JSON?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
