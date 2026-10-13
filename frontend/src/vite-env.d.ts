/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_WS_URL?: string;
  /** Public Agora App ID. Must match backend AGORA_APP_ID. Never put the App Certificate here. */
  readonly VITE_AGORA_APP_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
