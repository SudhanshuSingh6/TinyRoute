import { useState, useCallback } from "react";

export function useConfirmAction() {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const trigger = useCallback(() => setOpen(true), []);
  const close = useCallback(() => setOpen(false), []);

  // Runs asyncFn with loading bookkeeping.
  // Auto-closes dialog on success; calls onError(err) on failure.
  const confirm = useCallback(async (asyncFn, onError) => {
    setLoading(true);
    try {
      await asyncFn();
      setOpen(false);
    } catch (error) {
      onError?.(error);
    } finally {
      setLoading(false);
    }
  }, []);

  return { open, loading, trigger, close, confirm };
}
