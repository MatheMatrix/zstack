-- ZSV-10538 snapshot commit-delete idempotency: latch column
CALL ADD_COLUMN('VolumeSnapshotVO', 'deletingSince', 'TIMESTAMP', 1, NULL);
