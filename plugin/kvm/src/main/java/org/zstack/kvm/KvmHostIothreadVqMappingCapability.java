package org.zstack.kvm;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KvmHostIothreadVqMappingCapability {
    static final String MIN_QEMU_KVM_PACKAGE_VERSION = "6.2.0-451";
    static final String MIN_LIBVIRT_PACKAGE_VERSION = "8.0.0-163";
    private static final Pattern NUMERIC_VERSION_TOKEN = Pattern.compile("\\d+(?:\\.\\d+)*");

    public static boolean supported(String qemuKvmPackageVersion, String libvirtPackageVersion) {
        return versionAtLeast(qemuKvmPackageVersion, MIN_QEMU_KVM_PACKAGE_VERSION)
                && versionAtLeast(libvirtPackageVersion, MIN_LIBVIRT_PACKAGE_VERSION);
    }

    private static boolean versionAtLeast(String current, String required) {
        String normalized = normalizeVersion(current);
        if (normalized == null) {
            return false;
        }

        return new ComparableVersion(normalized).compareTo(new ComparableVersion(required)) >= 0;
    }

    private static String normalizeVersion(String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }

        String normalized = Arrays.stream(version.trim().split("\\s+")[0].split("-"))
                .map(KvmHostIothreadVqMappingCapability::numericPrefix)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("-"));
        return StringUtils.isBlank(normalized) ? null : normalized;
    }

    private static String numericPrefix(String versionSegment) {
        Matcher matcher = NUMERIC_VERSION_TOKEN.matcher(versionSegment);
        return matcher.lookingAt() ? matcher.group() : null;
    }
}
