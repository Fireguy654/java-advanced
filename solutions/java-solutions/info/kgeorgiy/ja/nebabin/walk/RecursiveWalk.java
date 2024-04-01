package info.kgeorgiy.ja.nebabin.walk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RecursiveWalk extends AbstractWalk {
    @Override
    protected void handlePath(Path path, AbstractFileHasher hasher) throws IOException {
        Files.walkFileTree(path, hasher);
    }

    public static void main(String[] args) {
        walkBase(args, new RecursiveWalk());
    }
}
