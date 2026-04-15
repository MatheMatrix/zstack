-- ZSTAC-83966: Add hidden field to AlarmVO/EventSubscriptionVO/ActiveAlarmVO for soft-hide support
CALL ADD_COLUMN('AlarmVO', 'hidden', 'TINYINT(1)', 0, '0');
CALL ADD_COLUMN('EventSubscriptionVO', 'hidden', 'TINYINT(1)', 0, '0');
CALL ADD_COLUMN('ActiveAlarmVO', 'hidden', 'TINYINT(1)', 0, '0');
