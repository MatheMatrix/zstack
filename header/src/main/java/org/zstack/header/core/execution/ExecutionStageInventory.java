package org.zstack.header.core.execution;

import org.zstack.header.configuration.PythonClassInventory;

import java.io.Serializable;
import java.sql.Timestamp;

@PythonClassInventory
public class ExecutionStageInventory implements Serializable {
    private String stageUuid;
    private String parentStageUuid;
    private String name;
    private String kind;
    private String state;
    private String nodeUuid;
    private String waitingOn;
    private String waitingReason;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private Long elapsedMs;

    public String getStageUuid() {
        return stageUuid;
    }

    public void setStageUuid(String stageUuid) {
        this.stageUuid = stageUuid;
    }

    public String getParentStageUuid() {
        return parentStageUuid;
    }

    public void setParentStageUuid(String parentStageUuid) {
        this.parentStageUuid = parentStageUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getNodeUuid() {
        return nodeUuid;
    }

    public void setNodeUuid(String nodeUuid) {
        this.nodeUuid = nodeUuid;
    }

    public String getWaitingOn() {
        return waitingOn;
    }

    public void setWaitingOn(String waitingOn) {
        this.waitingOn = waitingOn;
    }

    public String getWaitingReason() {
        return waitingReason;
    }

    public void setWaitingReason(String waitingReason) {
        this.waitingReason = waitingReason;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp startedAt) {
        this.startedAt = startedAt;
    }

    public Timestamp getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Timestamp finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
