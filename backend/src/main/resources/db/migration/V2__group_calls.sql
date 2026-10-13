-- Group calls: a call can have many participants instead of exactly one callee.

-- Group-started calls have no single callee.
ALTER TABLE calls MODIFY callee_id BIGINT UNSIGNED NULL;

CREATE TABLE call_participants (
    call_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invited_by BIGINT UNSIGNED NULL,
    invited_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    joined_at DATETIME(3) NULL,
    left_at DATETIME(3) NULL,
    PRIMARY KEY (call_id, user_id),
    CONSTRAINT fk_call_participants_call FOREIGN KEY (call_id) REFERENCES calls(id) ON DELETE CASCADE,
    CONSTRAINT fk_call_participants_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_call_participants_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_call_participants_state CHECK (state IN ('INVITED','JOINED','DECLINED','LEFT','MISSED')),
    INDEX idx_call_participants_user_state (user_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Close calls that were left RINGING/ACCEPTED long ago (e.g. a closed tab) so
-- they do not keep their users "busy" after the upgrade.
UPDATE calls
SET status = 'FAILED', end_reason = 'FAILED_NETWORK', ended_at = started_at
WHERE status IN ('RINGING', 'ACCEPTED')
  AND started_at < (UTC_TIMESTAMP(3) - INTERVAL 1 DAY);

-- Backfill participants for existing 1-to-1 calls.
INSERT INTO call_participants (call_id, user_id, state, invited_by, invited_at, joined_at, left_at)
SELECT id,
       caller_id,
       CASE WHEN status IN ('RINGING', 'ACCEPTED') THEN 'JOINED' ELSE 'LEFT' END,
       NULL,
       started_at,
       started_at,
       ended_at
FROM calls;

INSERT INTO call_participants (call_id, user_id, state, invited_by, invited_at, joined_at, left_at)
SELECT id,
       callee_id,
       CASE status
           WHEN 'RINGING' THEN 'INVITED'
           WHEN 'ACCEPTED' THEN 'JOINED'
           WHEN 'ENDED' THEN 'LEFT'
           WHEN 'REJECTED' THEN 'DECLINED'
           ELSE 'MISSED'
       END,
       caller_id,
       started_at,
       answered_at,
       CASE WHEN status = 'ENDED' THEN ended_at ELSE NULL END
FROM calls
WHERE callee_id IS NOT NULL;
