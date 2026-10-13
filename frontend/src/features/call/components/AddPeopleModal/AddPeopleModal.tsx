import React, { useEffect, useState } from 'react';
import { Modal, Button, Alert } from '../../../../shared/components/ui';
import { PeoplePicker } from '../../../chat/components/PeoplePicker';

interface AddPeopleModalProps {
  open: boolean;
  onClose: () => void;
  /** Everyone already ringing or in the call. */
  excludeIds: number[];
  /** How many more people fit in the call. */
  room: number;
  onInvite: (userIds: number[]) => Promise<void>;
  /** A 1-to-1 chat becomes a new group chat when someone is added. */
  createsGroup: boolean;
}

export function AddPeopleModal({ open, onClose, excludeIds, room, onInvite, createsGroup }: AddPeopleModalProps) {
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setSelected([]);
      setError(null);
      setBusy(false);
    }
  }, [open]);

  const submit = async () => {
    if (selected.length === 0 || busy) return;
    setBusy(true);
    setError(null);
    try {
      await onInvite(selected);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add people');
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Add people to the call" size="md">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
        {createsGroup && (
          <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
            Adding someone creates a group chat with everyone in this call.
          </p>
        )}

        <PeoplePicker selected={selected} onChange={setSelected} excludeIds={excludeIds} max={room} />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 'var(--space-3)' }}>
          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
            {room <= 0 ? 'The call is full' : `Up to ${room} more ${room === 1 ? 'person' : 'people'}`}
          </span>
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <Button variant="ghost" onClick={onClose}>Cancel</Button>
            <Button variant="primary" onClick={() => void submit()} loading={busy} disabled={selected.length === 0}>
              Add to call
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
