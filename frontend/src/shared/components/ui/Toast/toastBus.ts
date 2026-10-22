import { useCallback, useEffect, useState } from 'react';
import type { ToastData } from './Toast';

const TOAST_EVENT = 'app:toast';

let toastSeq = 0;

/** Shows a toast in the app-wide stack (rendered once by AppLayout). */
export function showToast(type: ToastData['type'], message: string): void {
  const toast: ToastData = { id: `${Date.now()}-${toastSeq++}`, type, message };
  document.dispatchEvent(new CustomEvent<ToastData>(TOAST_EVENT, { detail: toast }));
}

/** The app-wide toast stack: everything passed to showToast, until dismissed. */
export function useToastStack() {
  const [toasts, setToasts] = useState<ToastData[]>([]);

  useEffect(() => {
    const onToast = (e: Event) => {
      const toast = (e as CustomEvent<ToastData>).detail;
      setToasts((prev) => [...prev, toast]);
    };
    document.addEventListener(TOAST_EVENT, onToast);
    return () => document.removeEventListener(TOAST_EVENT, onToast);
  }, []);

  const remove = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return { toasts, remove };
}
