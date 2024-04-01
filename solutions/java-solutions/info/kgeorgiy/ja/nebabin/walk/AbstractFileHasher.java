package info.kgeorgiy.ja.nebabin.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class AbstractFileHasher extends SimpleFileVisitor<Path> {
    private final BufferedWriter output;

    public AbstractFileHasher(BufferedWriter output) {
        super();
        this.output = output;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        output.write(defaultHash() + " " + file.toString());
        output.newLine();
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        visitFile(file);
        return FileVisitResult.CONTINUE;
    }

    public void visitFile(Path file) throws IOException {
        String hash;
        try (InputStream info = Files.newInputStream(file)) {
            byte[] data = new byte[1024];
            int cnt;
            while ((cnt = info.read(data)) >= 0) {
                update(data, cnt);
            }
            hash = digest();
        } catch (IOException e) {
            hash = defaultHash();
        }
        output.write(hash + " " + file);
        output.newLine();
    }

    protected abstract void update(byte[] info, int cnt);

    protected abstract String defaultHash();

    protected abstract String digest();
}
