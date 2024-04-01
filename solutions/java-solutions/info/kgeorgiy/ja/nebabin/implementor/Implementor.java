package info.kgeorgiy.ja.nebabin.implementor;

import info.kgeorgiy.ja.nebabin.Impler;
import info.kgeorgiy.ja.nebabin.ImplerException;
import info.kgeorgiy.ja.nebabin.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates an implementation of class or interface.
 * It implements interface {@link Impler} and {@link JarImpler}.
 * It can be executed using two different modes:
 * 1) by giving one parameter – the full name of the class, which needs to be implemented
 *    In this mode it implements class with given name as defined in {@link #implement(Class, Path)} trying to load Class with {@link Class#forName(String)} and using current path as a Path parameter.
 * 2) by giving three parameters – "-jar", full name of the class, which needs to be implemented, "*.jar" (name of the jar file, in which class is implemented)
 *    In this mode it implements class as defined in mode 1 and puts it into a jar file with specified name.
 *
 * @author Nebabin Nikita
 */
public class Implementor implements Impler, JarImpler {
    /**
     * Creates an instance of {@link Implementor}.
     * Uses default constructor of {@link Object} to create an instance.
     */
    public Implementor() {
        super();
    }

    /**
     * Produces code implementing class or interface.
     * This code is placed in such directory, as if the root directory was {@code root} and the java file was at the same package as {@code token}.
     * Java file is named using name of {@code token} with "Impl" suffix.
     *
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException if it is impossible to create correct implementation as defined in {@link Impler#implement(Class, Path)}.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        root = root.normalize();
        if (token.equals(Enum.class) || token.equals(Record.class)) {
            throw new ImplerException("Can implement only classes and interfaces");
        }
        if (Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Can't implement a class with final modifier '" + token + "'");
        }
        if (isPrivateType(token)) {
            throw new ImplerException("Can't implement a class or interface with private modifier '" + token + "'");
        }
        List<Constructor<?>> implConstructors = List.of();
        if (!token.isInterface()) {
            implConstructors = Arrays.stream(token.getDeclaredConstructors())
                    .filter(Implementor::isVisibleConstructor).toList();
            if (implConstructors.isEmpty()) {
                throw new ImplerException("Have no visible constructors.");
            }
        }
        List<Method> implMethods = getMethods(token);
        throwingForEach(implMethods, Implementor::checkMethod);
        Path dir = root.resolve(getDirName(token));
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        try (BufferedWriter gen = Files.newBufferedWriter(Path.of(dir.toString(), token.getSimpleName() + "Impl.java"))) {
            writeLine(gen, 0, "package %s;", token.getPackageName());
            writeBlankLine(gen);
            writeLine(gen, 0, "public class %sImpl %s %s {",
                    token.getSimpleName(), token.isInterface() ? "implements" : "extends", token.getCanonicalName());
            throwingForEach(implConstructors, constructor -> printConstructor(gen, constructor));
            throwingForEach(implMethods, method -> printMethod(gen, method));
            writeEndBlock(gen, 0);
            writeBlankLine(gen);
        } catch (IOException e) {
            throw new ImplerException("Found a problem with writing", e);
        }
    }

    /**
     * Creates jar file implementing give interface or class.
     * It implements given {@code token} as defined in {@link #implement(Class, Path)} where {@link Path} argument is current working directory resolved by temporary directory.
     * It places jar file in specified {@code jarFile} {@link Path} argument.
     *
     * @param token type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if impossible to create implementation or transfer to jar file.
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        jarFile = jarFile.normalize();
        Path workRoot;
        try {
            workRoot = Files.createTempDirectory(Path.of(""), "ImplTemp");
        } catch (IOException e) {
            throw new ImplerException("Can't create temporary directory", e);
        }
        implement(token, workRoot);
        try {
            compile(workRoot, workRoot.resolve(getImplName(token) + ".java"), token);
        } catch (IllegalStateException e) {
            throw new ImplerException("Can't compile implementation", e);
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jarFile.toFile()))) {
            out.putNextEntry(new ZipEntry(getImplName(token, "/") + ".class"));
            Files.copy(workRoot.resolve(getImplName(token) + ".class"), out);
            out.closeEntry();
        } catch (IOException e) {
            throw new ImplerException("Can't transfer to jar", e);
        }
        try {
            Files.walkFileTree(workRoot, DELETE_VISITOR);
        } catch (IOException ignored) {}
    }

    /**
     * Compiles implementation of {@code token} which is placed at {@code implPath} and places the result nearby implementation.
     * It throws {@link IllegalStateException} in case of errors of compiling.
     *
     * @param root is placement of root of {@code token} packages.
     * @param implPath is placement of implementation.
     * @param token is class, which was implemented.
     */
    private static void compile(final Path root, final Path implPath, Class<?> token) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Have no compiler");
        }
        final String classpath = root + File.pathSeparator + getClassPath(token);
        final String[] args = {implPath.toString(), "-cp", classpath};
        final int exitCode = compiler.run(null, null, null, args);
        if (exitCode != 0) {
            throw new IllegalStateException("A problem with compiling occurred");
        }
    }

    /**
     * Returns class path of given {@code token}.
     * If it is impossible {@code token} class path, "." is returned.
     *
     * @param token is class which class path is returned.
     * @return class path of {@code}
     */
    private static String getClassPath(Class<?> token) {
        try {
            var codeSource = token.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return ".";
            }
            var location = codeSource.getLocation();
            if (location == null) {
                return ".";
            }
            return Path.of(location.toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns full name of an implementation of {@code token}.
     * It uses {@link File#separator} as separation in file tree.
     *
     * @param token is class which implementation name is returned.
     * @return implementation name of given {@code token}.
     */
    private static String getImplName(Class<?> token) {
        return getImplName(token, File.separator);
    }

    /**
     * Returns full name of an implementation of {@code token} using {@code sep} for separation in file tree.
     *
     * @param token is class which implementation name is returned.
     * @param sep is separator which used for separation in file tree.
     * @return implementation name of given {@code token}.
     */
    private static String getImplName(Class<?> token, String sep) {
        return getDirName(token, sep) + token.getSimpleName() + "Impl";
    }

    /**
     * Returns package name of {@code token} as file path {@link String}.
     * It uses {@link File#separator} as separation in file tree.
     *
     * @param token is class which package is returned.
     * @return package of given {@code token} as file path.
     */
    private static String getDirName(Class<?> token) {
        return getDirName(token, File.separator);
    }

    /**
     * Returns package of {@code token} as file path {@link String} using {@code sep} for separation in file tree.
     *
     * @param token is class which package is returned.
     * @param sep is separator which used for separation in file tree.
     * @return package of given {@code token} as file path.
     */
    private static String getDirName(Class<?> token, String sep) {
        return token.getPackageName().replace(".", sep) + sep;
    }

    /**
     * Implements given class or interface and can place it into jar if necessary.
     * It has two different modes of executing:
     * 1) by giving one parameter – the full name of the class, which needs to be implemented
     *    In this mode it implements class with given name as defined in {@link #implement(Class, Path)} trying to load Class with {@link Class#forName(String)} and using current path as a Path parameter.
     * 2) by giving three parameters – "-jar", full name of the class, which needs to be implemented, "*.jar" (name of the jar file, in which class is implemented)
     *    In this mode it implements class as defined in mode 1 and puts it into a jar file with specified name.
     * In case of impossibility of creating an implementation or incorrect use of this Method prints information in standard error output, using {@link System#err}
     *
     * @param args contains of 1 or 3 parameters, describing different modes of executing
     */
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 3) {
            System.err.println("Incorrect amount of params");
            return;
        }
        if (args.length == 3 && (!args[0].equals("-jar") || !args[2].endsWith(".jar"))) {
            System.err.println("Incorrect use of jar mode: first parameter must be '-jar', " +
                                "second must be the name of jar file");
        }
        Class<?> typeToken;
        try {
            typeToken = Class.forName(args[args.length == 1 ? 0 : 1]);
        } catch (ClassNotFoundException e) {
            System.err.println("Couldn't find a class for name '" + args[args.length == 1 ? 0 : 1] + "'");
            return;
        }
        Implementor implementor = new Implementor();
        try {
            if (args.length == 3) {
                implementor.implementJar(typeToken, Paths.get(args[2]));
            } else {
                implementor.implement(typeToken, Paths.get("").toAbsolutePath());
            }
        } catch(ImplerException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Checks possibility of placing {@code method} into implementation.
     * If it contains private types, which makes impossible to place method into implementation, throws {@link ImplerException}.
     *
     * @param method is a method to check.
     * @throws ImplerException if method contains private types, which are unreachable to implementation
     */
    private static void checkMethod(Method method) throws ImplerException {
        Optional<Class<?>> privType = Stream.concat(
                Stream.of(method.getReturnType()),
                Arrays.stream(method.getParameterTypes())
        ).filter(Implementor::isPrivateType).findAny();
        if (privType.isPresent()) {
            throw new ImplerException("Method '" + method + "' contains private declared type '"
                    + privType.get() + "'and can't be implemented.");
        }
    }

    /**
     * Checks if {@code constructor} is visible to implementation.
     * If {@code constructor} is private or contains private parameters, then it returns false, otherwise – true.
     *
     * @param constructor is a constructor to check.
     * @return value describing visibility of {@code constructor}
     */
    private static boolean isVisibleConstructor(Constructor<?> constructor) {
        if (isPrivateMember(constructor)) {
            return false;
        }
        return Arrays.stream(constructor.getParameterTypes()).noneMatch(Implementor::isPrivateType);
    }

    /**
     * Prints a {@code method} for the implementation.
     * The result of this method is well-defined only if it is used to print non-abstract and non-private method.
     * Printed method ignores its arguments and returns default value of its return type.
     *
     * @param writer is a buffered writer by which method is printed.
     * @param method is a method to print.
     * @throws ImplerException if an {@link IOException} occurs.
     */
    private static void printMethod(BufferedWriter writer, Method method) throws ImplerException {
        writeBlankLine(writer);
        writeLine(writer, 1, getMemberModifierString(method) + "%s %s(%s) {",
                method.getReturnType().getCanonicalName(),
                method.getName(),
                joinAsArgs(method.getParameterTypes()));
        writeLine(writer, 2, "return" + (void.class == method.getReturnType() ? ";" : " %s;"),
                defValue(method.getReturnType()));
        writeEndBlock(writer, 1);
    }

    /**
     * Prints a {@code constructor} for the implementation.
     * The result of this constructor is well-defined only if it is used to print non-private constructor.
     * Printed constructor calls a constructor of super class with the same signature.
     *
     * @param writer is a by which constructor is printed.
     * @param constructor is a constructor to print.
     * @throws ImplerException if an {@link IOException} occurs.
     */
    private static void printConstructor(BufferedWriter writer, Constructor<?> constructor) throws ImplerException {
        writeBlankLine(writer);
        writeLine(writer, 1, getMemberModifierString(constructor) + "%sImpl(%s) %s{",
                constructor.getDeclaringClass().getSimpleName(),
                joinAsArgs(constructor.getParameterTypes()),
                getThrowsString(constructor)
        );
        writeLine(writer, 2, "super(%s);",
                collectAsArgs(IntStream.range(0, constructor.getParameterTypes().length).mapToObj(ARG_NAMING)));
        writeEndBlock(writer, 1);
    }

    /**
     * Gets {@link String}, which is prefix of a {@code member} declaration containing access modifiers information.
     * The result of this method is well-formed only if {@code member} is not private.
     *
     * @param member is a member, which access modifier method returns.
     * @return prefix of a {@code member} declaration contains access modifiers.
     */
    private static String getMemberModifierString(Member member) {
        int modifiers = member.getModifiers();
        if (Modifier.isPublic(modifiers)) {
            return "public ";
        } else if (Modifier.isProtected(modifiers)) {
            return "protected ";
        } else {
            return "";
        }
    }

    /**
     * Gets {@link String}, which is suffix of constructor declaration containing information about possibility of throwing exceptions.
     *
     * @param constructor is constructor which information method returns.
     * @return suffix of {@code constructor} declaration containing information about possibility of throwing exceptions.
     */
    private static String getThrowsString(Constructor<?> constructor) {
        Class<?>[] types = constructor.getExceptionTypes();
        if (types.length == 0) {
            return "";
        } else {
            return "throws " + collectAsArgs(Arrays.stream(types).map(Class::getCanonicalName)) + " ";
        }
    }

    /**
     * Gets {@link String} which contain default value for {@code type}.
     * Returns "null" for link types, "false" for boolean, "0" for other primitive types.
     *
     * @param type is class which default value is returned.
     * @return is default value for {@code type}.
     * @see <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">Primitive types</a>.
     */
    private static String defValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return "null";
        } else if (type.equals(boolean.class)) {
            return "false";
        } else {
            return "0";
        }
    }

    /**
     * Returns {@link String} containing all {@code argTypes} named and separated.
     *
     * @param argTypes are classes which names are returned named and joined.
     * @return full java names of {@code argTypes} naming with {@code arg%n}, where {@code %n} is serial number of argument, using {@value #LIST_SPLITTER} as a separator.
     */
    private static String joinAsArgs(Class<?>[] argTypes) {
        return collectAsArgs(Arrays.stream(argTypes).map(new ArgPlacer()));
    }

    /**
     * Unite elements of {@code stream} in one string separated by {@value #LIST_SPLITTER}.
     *
     * @param stream is stream of strings which is returned joined.
     * @return string separated by {@value #LIST_SPLITTER}.
     */
    private static String collectAsArgs(Stream<String> stream) {
        return stream.collect(Collectors.joining(LIST_SPLITTER));
    }

    /**
     * Gets all the abstract methods of {@code token}.
     *
     * @param token is class which abstract method are returned.
     * @return abstract methods of {@code token}.
     */
    private static List<Method> getMethods(Class<?> token) {
        Stream<Method> allMethods = Arrays.stream(token.getMethods());
        if (!token.isInterface()) {
            Class<?> curParent = token;
            while (curParent != null) {
                allMethods = Stream.concat(allMethods,
                        Arrays.stream(curParent.getDeclaredMethods()).filter(IS_PACKAGED));
                curParent = curParent.getSuperclass();
            }
            allMethods = allMethods
                    .collect(Collectors.toMap(MethodSignature::new, Function.identity(), MERGE_METHODS))
                    .values().stream();
        }
        return allMethods.filter(IS_ABSTRACT).toList();
    }


    /**
     * Consumes all the {@code elems} by {@code consumer}.
     *
     * @param elems are elements for consuming.
     * @param consumer is consumer which is a void function with single argument {@code <T>}, which can throw {@link ImplerException}.
     * @param <T> is type of element to be consumed.
     * @throws ImplerException if consumer throws it.
     */
    private static <T> void throwingForEach(List<T> elems, ImplerConsumer<T> consumer) throws ImplerException {
        for (T i: elems) {
            consumer.accept(i);
        }
    }

    /**
     * Writes a tabulated formatted by {@code format} line using {@code writer}.
     *
     * @param writer is buffered writer which is used to write a line.
     * @param tabCnt is an amount of tabulations before line with tabulation symbol '{@value #STR_ST}'.
     * @param format is a format String used for {@link String#format(String, Object...)}.
     * @param args are args used for {@link String#format(String, Object...)}.
     * @throws ImplerException if {@link IOException} occurs while writing.
     */
    private static void writeLine(BufferedWriter writer, int tabCnt, String format, Object... args) throws ImplerException {
        try {
            writer.write(STR_ST.repeat(tabCnt));
            writer.write(String.format(format, args).chars().mapToObj(CHARACTER_CODING).collect(Collectors.joining()));
            writer.newLine();
        } catch (IOException e) {
            throw new ImplerException("Can't write to file");
        }
    }

    /**
     * Writes an end of code block using {@code writer}.
     * Symbol "}" is used as an end of code block.
     *
     * @param writer is buffered writer which is used to write a line.
     * @param tabCnt is an amount of tabulations before line with tabulation symbol '{@value #STR_ST}'.
     * @throws ImplerException if an {@link IOException} occurs.
     */
    private static void writeEndBlock(BufferedWriter writer, int tabCnt) throws ImplerException {
        writeLine(writer, tabCnt, "}");
    }

    /**
     * Writes one blank line using {@code writer}.
     *
     * @param writer is buffered writer which is used to write a blank line.
     * @throws ImplerException if an {@link IOException} occurs.
     */
    private static void writeBlankLine(BufferedWriter writer) throws ImplerException {
        writeLine(writer, 0, "");
    }

    /**
     * Returns true only and only if {@code typeToken} is private.
     *
     * @param typeToken is class which access modifier is checked.
     * @return boolean value represents if {@code typeToken} is private.
     */
    private static boolean isPrivateType(Class<?> typeToken) {
        return Modifier.isPrivate(typeToken.getModifiers());
    }

    /**
     * Returns true only and only if {@code member} is private.
     *
     * @param member is member which access modifier is checked.
     * @return boolean value represents if {@code member} is private.
     */
    private static boolean isPrivateMember(Member member) {
        return Modifier.isPrivate(member.getModifiers());
    }

    /**
     * It returns arg followed by number – given argument.
     * It is a static final function to avoid creating a new lambda in every usage.
     */
    private static final IntFunction<String> ARG_NAMING = num -> "arg" + num;
    /**
     * ASCII encoder which provides possibility to check if char can be encoded by {@link CharsetEncoder#canEncode(char)}.
     */
    private static final CharsetEncoder ENCODER = StandardCharsets.US_ASCII.newEncoder();
    /**
     * Codes character as its symbol if it can be encoded and as its codepoint otherwise.
     */
    private static final IntFunction<String> CHARACTER_CODING =
            ch -> (ENCODER.canEncode((char) ch) ? Character.toString(ch) : String.format("\\u%04X", ch));
    /**
     * It returns latest method with the deepest return type in inheritance tree.
     * It is a static final function to avoid creating a new lambda in every usage.
     */
    private static final BinaryOperator<Method> MERGE_METHODS = (fir, sec) -> (Modifier.isPublic(sec.getModifiers()) ?
            (sec.getReturnType().isAssignableFrom(fir.getReturnType()) ? fir : sec) : fir);
    /**
     * It returns true only and only if given {@link Member} is protected or package-private.
     * It is a static final function to avoid creating a new lambda in every usage.
     */
    private static final Predicate<Member> IS_PACKAGED = ((Predicate<Member>) Implementor::isPrivateMember)
            .or(member -> Modifier.isPublic(member.getModifiers())).negate();
    /**
     * It returns true only and only if given {@link Member} is abstract.
     * It is a static final function to avoid creating a new lambda in every usage.
     */
    private static final Predicate<Member> IS_ABSTRACT = member -> Modifier.isAbstract(member.getModifiers());
    /**
     * Tabulation string which is used in strings in implementation to show the nesting depths of code blocks.
     */
    private static final String STR_ST = "\t";
    /**
     *  It is used as a separator to list text tokens in code.
     */
    private static final String LIST_SPLITTER = ", ";
    /**
     * Anonymized class which extends simple file visitor recursively deletes directories.
     */
    private static final SimpleFileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<>() {
        /**
         * Deletes file on visiting.
         *
         * @param file
         *          a reference to the file
         * @param attrs
         *          the file's basic attributes
         *
         * @return {@link FileVisitResult#CONTINUE}.
         * @throws IOException if deletion is impossible.
         */
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Deletes directory after visiting.
         *
         * @param dir
         *          a reference to the directory
         * @param exc
         *          {@code null} if the iteration of the directory completes without
         *          an error; otherwise the I/O exception that caused the iteration
         *          of the directory to complete prematurely
         *
         * @return {@link FileVisitResult#CONTINUE}.
         * @throws IOException if deletion is impossible.
         */
        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

    /**
     * It is a functional interface which consumes a value and can throw {@link ImplerException}.
     *
     * @param <T> is type of value which can be consumed by instance of implementation of this interface.
     * @author Nebabin Nikita
     */
    @FunctionalInterface
    private interface ImplerConsumer<T> {
        /**
         * Performs action on {@code value}.
         *
         * @param value with type {@code <T>} action on which is performed.
         * @throws ImplerException when action can't be performed.
         */
        void accept(T value) throws ImplerException;
    }

    /**
     * It converts {@link Class} to {@link String} by following {@link Class} with name.
     *
     * @author Nebabin Nikita
     */
    private static class ArgPlacer implements Function<Class<?>, String> {
        /**
         * A counter which contains first serial number not occupied by {@link Class}.
         */
        private int argCnt = 0;

        /**
         * Creates an instance of {@link ArgPlacer}.
         * Uses default constructor of {@link Object} to create an instance.
         */
        private ArgPlacer() {}

        /**
         * It converts {@link Class} to {@link String} by following {@link Class} with name.
         * {@link Class} is named "arg" followed by the first non-occupied number.
         *
         * @param type the function argument
         * @return class followed by name.
         */
        @Override
        public String apply(Class<?> type) {
            return type.getCanonicalName() + " arg" + (argCnt++);
        }
    }

    /**
     * A record which represents method signature.
     * It consists of {@code name} of the method and types of its arguments.
     *
     * @param name is the name of the method.
     * @param argTypes are types of the arguments of the method.
     * @author Nebabin Nikita
     */
    private record MethodSignature(String name, Class<?>[] argTypes) {
        /**
         * Creates an instance of record which contains signature of specified {@code method}.
         *
         * @param method is method which signature is assigned to instance of record.
         */
        private MethodSignature(Method method) {
            this(method.getName(), method.getParameterTypes());
        }

        /**
         * Compares to {@link Object}.
         * The comparison return true only and only if {@code obj} is an instance of {@link MethodSignature} with same signature
         *
         * @param obj is the reference object with which to compare.
         * @return true if {@code obj} is an instance of {@link MethodSignature} with same signature.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof MethodSignature other) {
                return name.equals(other.name) && Arrays.equals(argTypes, other.argTypes);
            }
            return false;
        }

        /**
         * Returns hashcode for an instance of {@link MethodSignature}.
         *
         * @return hashcode for an instance of {@link MethodSignature}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(name, Arrays.hashCode(argTypes));
        }
    }
}
