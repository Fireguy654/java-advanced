package info.kgeorgiy.ja.nebabin.walk;

import java.io.BufferedWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ShaOneFileHasher extends AbstractFileHasher {
    private final MessageDigest md;

    public ShaOneFileHasher(BufferedWriter output) {
        super(output);
        MessageDigest tmpMd = null;
        try {
            tmpMd = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {}
        md = tmpMd;
    }

    @Override
    protected void update(byte[] info, int cnt) {
        md.update(info, 0, cnt);
    }

    @Override
    protected String defaultHash() {
        return "0".repeat(md.getDigestLength() * 2);
    }

    @Override
    protected String digest() {
        return HexFormat.of().formatHex(md.digest());
    }
}
