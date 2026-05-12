package org.zstack.core.ansible;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * Created by frank on 7/22/2015.
 */
public class PrepareAnsible {
    private final static CLogger logger = Utils.getLogger(PrepareAnsible.class);

    private String targetIp;

    private static List<String> hostIPs = new ArrayList<String>();
    private static File hostsFile = new File(AnsibleConstant.INVENTORY_FILE);

    private static ReentrantLock lock = new ReentrantLock();

    static {
        try {
            if (!hostsFile.exists() && !hostsFile.createNewFile()) {
                throw new OperationFailureException(operr(ORG_ZSTACK_CORE_ANSIBLE_10001, "fail to create new File[%s]", hostsFile));
            }

            String ipStr = FileUtils.readFileToString(hostsFile);
            List<String> cleanedHostIPs = new ArrayList<String>();
            boolean inventoryChanged = false;
            for (String ip : ipStr.split("\n")) {
                ip = ip.trim();
                ip = StringUtils.strip(ip, "\n\t\r");
                if (ip.equals("")) {
                    continue;
                }

                if (isInvalidInventoryLine(ip)) {
                    inventoryChanged = true;
                    continue;
                }

                cleanedHostIPs.add(ip);
                if (AnsibleGlobalProperty.KEEP_HOSTS_FILE_IN_MEMORY) {
                    hostIPs.add(ip);
                }
            }

            if (inventoryChanged) {
                String content = cleanedHostIPs.isEmpty() ? "" : StringUtils.join(cleanedHostIPs, "\n") + "\n";
                try {
                    writeStringToFileAtomically(hostsFile, content);
                } catch (Exception e) {
                    logger.warn(String.format("atomic inventory write failed, falling back to direct write: %s", e.getMessage()));
                    FileUtils.writeStringToFile(hostsFile, content, false);
                }
            }
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        }
    }

    private static final String[] SSH_KEY_PATTERNS = {
        "ssh-rsa", "ssh-ed25519", "ssh-dss",
        "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521"
    };

    private static boolean isInvalidInventoryLine(String line) {
        for (String pattern : SSH_KEY_PATTERNS) {
            if (line.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void writeStringToFileAtomically(File targetFile, String content) throws IOException {
        Path target = targetFile.toPath();
        Path temp = Files.createTempFile(target.getParent(), targetFile.getName(), ".tmp");
        try {
            try (FileOutputStream outputStream = new FileOutputStream(temp.toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream, Charset.defaultCharset())) {
                writer.write(content);
                writer.flush();
                outputStream.getFD().sync();
            }
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public String getTargetIp() {
        return targetIp;
    }

    public PrepareAnsible setTargetIp(String targetIp) {
        this.targetIp = targetIp;
        return this;
    }

    private boolean findIpInHostFile() throws IOException {
        BufferedReader bf = new BufferedReader(new FileReader(AnsibleConstant.INVENTORY_FILE));
        String line;

        try {
            while ((line = bf.readLine()) != null) {
                line = StringUtils.strip(line.trim(), "\t\r\n");
                if (line.equals(targetIp.trim())) {
                    return true;
                }
            }

            return false;
        } finally {
            bf.close();
        }
    }

    private void setupHostsFile() throws IOException {
        lock.lock();
        try {
            if (AnsibleGlobalProperty.KEEP_HOSTS_FILE_IN_MEMORY) {
                if (!hostIPs.contains(targetIp)) {
                    hostIPs.add(targetIp);
                    FileUtils.writeStringToFile(hostsFile, StringUtils.join(hostIPs, "\n"), false);
                    logger.debug(String.format("add target ip[%s] to %s", targetIp, AnsibleConstant.INVENTORY_FILE));
                }
            } else {
                if (!findIpInHostFile()) {
                    FileUtils.writeStringToFile(hostsFile, String.format("%s\n", targetIp), true);
                    logger.debug(String.format("add target ip[%s] to %s", targetIp, AnsibleConstant.INVENTORY_FILE));
                } else {
                    logger.debug(String.format("found target ip[%s] in %s", targetIp, AnsibleConstant.INVENTORY_FILE));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void prepare() {
        DebugUtils.Assert(targetIp  != null, "targetIp cannot be null");

        try {
            setupHostsFile();
        } catch (IOException e) {
            throw new CloudRuntimeException(e);
        }
    }
}

