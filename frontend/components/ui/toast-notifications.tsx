'use client';

import { useState, useEffect, useCallback, useRef, createContext, useContext } from 'react';

type ToastType = 'error' | 'success' | 'info';

interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

interface ToastContextValue {
  showToast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextValue>({ showToast: () => {} });

let globalShowToast: (message: string, type?: ToastType) => void = () => {};

export function useToast() {
  return useContext(ToastContext);
}

/** Call this from anywhere (even outside React) to show a toast */
export function showToast(message: string, type: ToastType = 'error') {
  globalShowToast(message, type);
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextIdRef = useRef(0);

  const addToast = useCallback((message: string, type: ToastType = 'error') => {
    const id = ++nextIdRef.current;
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 5000);
  }, []);

  useEffect(() => {
    globalShowToast = addToast;
    return () => {
      globalShowToast = () => {};
    };
  }, [addToast]);

  const dismiss = (id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const bgColor = (type: ToastType) => {
    switch (type) {
      case 'error': return 'bg-red-600';
      case 'success': return 'bg-green-600';
      case 'info': return 'bg-blue-600';
    }
  };

  return (
    <ToastContext.Provider value={{ showToast: addToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 max-w-sm">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`${bgColor(toast.type)} text-white px-4 py-3 rounded-lg shadow-lg text-sm flex items-start gap-2 animate-in slide-in-from-right`}
          >
            <span className="flex-1">{toast.message}</span>
            <button
              onClick={() => dismiss(toast.id)}
              className="text-white/80 hover:text-white font-bold ml-2"
            >
              x
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
