package dev.arcane.loader;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeys {
    private PemKeys() {
    }

    static PublicKey readPublic(String pem) throws Exception {
        return KeyFactory.getInstance("Ed25519").generatePublic(
            new X509EncodedKeySpec(decode(pem, "PUBLIC KEY"))
        );
    }

    static PrivateKey readPrivate(String pem) throws Exception {
        return KeyFactory.getInstance("Ed25519").generatePrivate(
            new PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY"))
        );
    }

    static String publicPem(PublicKey key) {
        return encode("PUBLIC KEY", key.getEncoded());
    }

    static String privatePem(PrivateKey key) {
        return encode("PRIVATE KEY", key.getEncoded());
    }

    private static byte[] decode(String pem, String type) {
        String body = pem
            .replace("-----BEGIN " + type + "-----", "")
            .replace("-----END " + type + "-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String encode(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
            + "\n-----END " + type + "-----\n";
    }
}
