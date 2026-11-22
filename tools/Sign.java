import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Minimal apksigner replacement: Sign <in.apk> <out.apk>. The key (PKCS12, alias "skillup") is not in the
 * repository: its path and password come from $SKILLUP_KEYSTORE and $SKILLUP_KEYSTORE_PASS.
 */
public class Sign {
    public static void main(String[] a) throws Exception {
        char[] pass = System.getenv("SKILLUP_KEYSTORE_PASS").toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(System.getenv("SKILLUP_KEYSTORE"))) {
            ks.load(in, pass);
        }
        PrivateKey key = (PrivateKey) ks.getKey("skillup", pass);
        X509Certificate cert = (X509Certificate) ks.getCertificate("skillup");
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "skillup", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(a[0]))
                .setOutputApk(new File(a[1]))
                .setMinSdkVersion(26)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
    }
}
