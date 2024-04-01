package info.kgeorgiy.ja.nebabin.walk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public abstract class AbstractWalk {
    public void walk(BufferedReader pathsFile, BufferedWriter output, String mode) throws WalkException {
        AbstractFileHasher hasher;
        switch (mode) {
            case "jenkins" -> hasher = new FileJenkinsHasher(output);
            case "sha-1" -> hasher = new ShaOneFileHasher(output);
            default -> throw new WalkModeException("Incorrect mode of hashing in walk.");
        }
        String curInput;
        try {
            while ((curInput = pathsFile.readLine()) != null) {
                Path curPath;
                try {
                    curPath = Path.of(curInput);
                } catch (InvalidPathException e) {
                    output.write(hasher.defaultHash() + " " + curInput);
                    output.newLine();
                    continue;
                }
                try {
                    handlePath(curPath, hasher);
                } catch (IOException e) {
                    throw new WalkIOException("Can't write to output file.", e);
                }
            }
        } catch (IOException e) {
            throw new WalkIOException("Can't read from input file.", e);
        }
    }

    abstract protected void handlePath(Path path, AbstractFileHasher hasher) throws IOException;

    public static Path getPath(String path) {
        try {
            return Path.of(path);
        } catch(NullPointerException | InvalidPathException e) {
            System.err.println("'" + path + "' is not a valid path");
            throw e;
        }
    }

    public static void walkBase(String[] args, AbstractWalk walker) {
        String mode = "jenkins";
        if (args == null || args.length < 2 || args.length > 3) {
            System.err.println("Incorrect amount of parameters.");
            return;
        }
        if (args.length == 3) {
            mode = args[2];
        }
        Path inpFile;
        Path outFile;
        try {
            inpFile = getPath(args[0]);
            outFile = getPath(args[1]);
        } catch (InvalidPathException e) {
            System.err.println("'" + e.getInput() + "' is not a valid path.");
            return;
        } catch (NullPointerException e) {
            System.err.println("Null path is unsupported.");
            return;
        }
        try {
            if (outFile.getParent() != null) {
                Files.createDirectories(outFile.getParent());
            }
        } catch (IOException e) {
            System.err.println("Can't create directories for output file.");
        }
        try (BufferedReader inp = Files.newBufferedReader(inpFile)) {
            try (BufferedWriter out = Files.newBufferedWriter(outFile)) {
                walker.walk(inp, out, mode);
            } catch (WalkException e) {
                System.err.println(e.getMessage());
            } catch (IOException e) {
                System.err.println("Can't create output file.");
            }
        } catch (IOException e) {
            System.err.println("Can't create input file.");
        }
    }
}
