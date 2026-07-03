UPDATE `zstack`.`ActiveAlarmTemplateVO`
SET `metricName` = 'CPUUsedUtilization'
WHERE `uuid` = 'c9e6cdca107140bea62b4ca919ff9e88'
  AND `metricName` = 'VRouterCPUAverageUsedUtilization';

UPDATE `zstack`.`AlarmVO`
SET `metricName` = 'CPUUsedUtilization'
WHERE `uuid` IN (
    SELECT `alarmUuid` FROM `zstack`.`ActiveAlarmVO`
    WHERE `templateUuid` = 'c9e6cdca107140bea62b4ca919ff9e88'
)
  AND `metricName` = 'VRouterCPUAverageUsedUtilization';
