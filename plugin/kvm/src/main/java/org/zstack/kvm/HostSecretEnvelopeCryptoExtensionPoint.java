package org.zstack.kvm;

public interface HostSecretEnvelopeCryptoExtensionPoint {
    byte[] seal(byte[] recipientPublicKey, byte[] plaintext) throws Exception;
}
