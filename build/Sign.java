import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Collections;

/**
 * Signiert ein Plugin-ZIP fuer ein Jameica-Repository (SHA1withRSA, Datei "&lt;zip&gt;.sha1").
 *
 * Aufruf:
 *   java build/Sign.java &lt;keystore&gt; &lt;storepass-datei&gt; &lt;zip&gt;
 */
public class Sign
{
  public static void main(String[] args) throws Exception
  {
    if (args.length != 3)
    {
      System.err.println("usage: java build/Sign.java <keystore> <storepass-file> <zip>");
      System.exit(1);
    }

    char[] pass = Files.readString(Path.of(args[1])).trim().toCharArray();
    KeyStore ks = KeyStore.getInstance(new java.io.File(args[0]),pass);

    String alias = null;
    for (String a:Collections.list(ks.aliases()))
    {
      if (ks.isKeyEntry(a))
      {
        alias = a;
        break;
      }
    }
    if (alias == null)
      throw new IllegalStateException("no private key in " + args[0]);

    PrivateKey key = (PrivateKey) ks.getKey(alias,pass);
    Certificate cert = ks.getCertificate(alias);
    byte[] data = Files.readAllBytes(Path.of(args[2]));

    Signature s = Signature.getInstance("SHA1withRSA");
    s.initSign(key);
    s.update(data);
    byte[] sig = s.sign();

    // Verify against the certificate before writing.
    Signature v = Signature.getInstance("SHA1withRSA");
    v.initVerify(cert.getPublicKey());
    v.update(data);
    if (!v.verify(sig))
      throw new IllegalStateException("signature verification failed");

    Path out = Path.of(args[2] + ".sha1");
    Files.write(out,sig);
    System.out.println("signed with alias \"" + alias + "\": " + out);
  }
}
