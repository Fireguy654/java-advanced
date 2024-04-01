package info.kgeorgiy.ja.nebabin.walk;

import java.io.IOException;
import java.nio.file.Path;

public class Walk extends AbstractWalk {
    @Override
    protected void handlePath(Path path, AbstractFileHasher hasher) throws IOException {
        hasher.visitFile(path);
    }

    public static void main(String[] args) {
        walkBase(args, new Walk());
    }
}
