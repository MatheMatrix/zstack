package org.zstack.sdk.iam2.container;

public enum IAM2ContainerResyncPhase {
	PRECHECK,
	ENSURE_USERS,
	ENSURE_PROJECT,
	ENSURE_MEMBERSHIPS,
	ENSURE_QUOTAS,
	VERIFY,
	DONE,
}
