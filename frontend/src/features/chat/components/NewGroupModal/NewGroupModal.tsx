import React, { useEffect, useState } from 'react';
import { Modal, Button, Input, Alert } from '../../../../shared/components/ui';
import type { Conversation } from '../../../../shared/types';
import { conversationApi } from '../../api/conversationApi';
import { PeoplePicker } from '../PeoplePicker';

interface NewGroupModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (conv: Conversation) => void;
}

const MIN_OTHERS = 2;
const MAX_OTHERS = 49;

export function NewGroupModal({ open, onClose, onCreated }: NewGroupModalProps) {
  const [title, setTitle] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setTitle('');
      setSelected([]);
      setError(null);
      setCreating(false);
    }
  }, [open]);

  const create = async () => {
    if (selected.length < MIN_OTHERS || creating) return;
    setCreating(true);
    setError(null);
    try {
      onCreated(await conversationApi.createGroup(selected, title));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the group');
      setCreating(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="New group" size="md">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

        <Input
          label="Group name (optional)"
          placeholder="e.g. Study group"
          value={title}
          maxLength={150}
          onChange={(e) => setTitle(e.target.value)}
          hint="Leave empty to show the members' names"
        />

        <PeoplePicker selected={selected} onChange={setSelected} max={MAX_OTHERS} />

        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 'var(--space-3)' }}>
          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
            {selected.length < MIN_OTHERS
              ? `Choose at least ${MIN_OTHERS} people`
              : `${selected.length + 1} people including you`}
          </span>
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginLeft: 'auto' }}>
            <Button variant="ghost" onClick={onClose}>Cancel</Button>
            <Button variant="primary" onClick={() => void create()} loading={creating} disabled={selected.length < MIN_OTHERS}>
              Create group
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
