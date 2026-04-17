package org.zstack.sdnController.header;

import org.zstack.header.message.NeedJsonSchema;
import org.zstack.header.network.sdncontroller.SdnControllerInventory;

public class SdnControllerCanonicalEvents {
    public static final String SDNCONTROLLER_STATE_CHANGED_PATH = "/sdnController/state/change";
    public static final String SDNCONTROLLER_STATUS_CHANGED_PATH = "/sdnController/status/change";

    // Sync events (ZCF-2133)
    public static final String SDN_CONTROLLER_SYNC_COMPLETED_PATH = "/sdnController/sync/completed";
    public static final String SDN_CONTROLLER_SYNC_FAILED_PATH = "/sdnController/sync/failed";
    public static final String SDN_CONTROLLER_SYNC_ALARM_PATH = "/sdnController/sync/alarm";
    public static final String SDN_CONTROLLER_SYNC_DIFF_OVER_THRESHOLD_PATH = "/sdnController/sync/diffOverThreshold";

    @NeedJsonSchema
    public static class SdnControllerStatusChangedData {
        private String sdnControllerUuid;
        private String sdnControllerType;
        private String oldStatus;
        private String newStatus;
        private SdnControllerInventory inv;

        public String getSdnControllerUuid() {
            return sdnControllerUuid;
        }

        public void setSdnControllerUuid(String sdnControllerUuid) {
            this.sdnControllerUuid = sdnControllerUuid;
        }

        public String getSdnControllerType() {
            return sdnControllerType;
        }

        public void setSdnControllerType(String sdnControllerType) {
            this.sdnControllerType = sdnControllerType;
        }

        public String getOldStatus() {
            return oldStatus;
        }

        public void setOldStatus(String oldStatus) {
            this.oldStatus = oldStatus;
        }

        public String getNewStatus() {
            return newStatus;
        }

        public void setNewStatus(String newStatus) {
            this.newStatus = newStatus;
        }

        public SdnControllerInventory getInv() {
            return inv;
        }

        public void setInv(SdnControllerInventory inv) {
            this.inv = inv;
        }
    }

    @NeedJsonSchema
    public static class SdnControllerSyncCompletedData {
        private String sdnControllerUuid;
        private int toCreateCount;
        private int toDeleteCount;
        private int toUpdateCount;
        private int failedCount;
        private boolean dryRun;
        private String scope;

        public String getSdnControllerUuid() { return sdnControllerUuid; }
        public void setSdnControllerUuid(String sdnControllerUuid) { this.sdnControllerUuid = sdnControllerUuid; }
        public int getToCreateCount() { return toCreateCount; }
        public void setToCreateCount(int toCreateCount) { this.toCreateCount = toCreateCount; }
        public int getToDeleteCount() { return toDeleteCount; }
        public void setToDeleteCount(int toDeleteCount) { this.toDeleteCount = toDeleteCount; }
        public int getToUpdateCount() { return toUpdateCount; }
        public void setToUpdateCount(int toUpdateCount) { this.toUpdateCount = toUpdateCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }

    @NeedJsonSchema
    public static class SdnControllerSyncFailedData {
        private String sdnControllerUuid;
        private String errorCode;
        private String errorDetails;
        private int consecutiveFailCount;

        public String getSdnControllerUuid() { return sdnControllerUuid; }
        public void setSdnControllerUuid(String sdnControllerUuid) { this.sdnControllerUuid = sdnControllerUuid; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public String getErrorDetails() { return errorDetails; }
        public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
        public int getConsecutiveFailCount() { return consecutiveFailCount; }
        public void setConsecutiveFailCount(int consecutiveFailCount) { this.consecutiveFailCount = consecutiveFailCount; }
    }

    @NeedJsonSchema
    public static class SdnControllerSyncAlarmData {
        private String sdnControllerUuid;
        private int consecutiveFailCount;
        private int alarmThreshold;

        public String getSdnControllerUuid() { return sdnControllerUuid; }
        public void setSdnControllerUuid(String sdnControllerUuid) { this.sdnControllerUuid = sdnControllerUuid; }
        public int getConsecutiveFailCount() { return consecutiveFailCount; }
        public void setConsecutiveFailCount(int consecutiveFailCount) { this.consecutiveFailCount = consecutiveFailCount; }
        public int getAlarmThreshold() { return alarmThreshold; }
        public void setAlarmThreshold(int alarmThreshold) { this.alarmThreshold = alarmThreshold; }
    }

    @NeedJsonSchema
    public static class SdnControllerSyncDiffOverThresholdData {
        private String sdnControllerUuid;
        private int totalDiff;
        private int threshold;

        public String getSdnControllerUuid() { return sdnControllerUuid; }
        public void setSdnControllerUuid(String sdnControllerUuid) { this.sdnControllerUuid = sdnControllerUuid; }
        public int getTotalDiff() { return totalDiff; }
        public void setTotalDiff(int totalDiff) { this.totalDiff = totalDiff; }
        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
    }

}
