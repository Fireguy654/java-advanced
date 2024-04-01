package info.kgeorgiy.ja.nebabin.walk;

import java.io.BufferedWriter;

public class FileJenkinsHasher extends AbstractFileHasher {
    private int hash;

    public FileJenkinsHasher(BufferedWriter output) {
        super(output);
        hash = 0;
    }

    @Override
    protected void update(byte[] info, int cnt) {
        for (int i = 0; i < cnt; ++i) {
            hash += Byte.toUnsignedInt(info[i]);
            hash += hash << 10;
            hash ^= hash >>> 6;
        }
    }

    @Override
    protected String defaultHash() {
        return getHexString(0);
    }

    @Override
    protected String digest() {
        hash += hash << 3;
        hash ^= hash >>> 11;
        hash += hash << 15;
        String res = getHexString(hash);
        hash = 0;
        return res;
    }

    private String getHexString(int num) {
        return String.format("%08x", num);
    }
}
