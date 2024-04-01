package info.kgeorgiy.ja.nebabin;

import info.kgeorgiy.ja.nebabin.implementor.Implementor;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.nio.file.Path;
import java.util.Arrays;

public class ExamplingSolutions {
    public static void main(String[] args) {
        try {
            Implementor tmp = new Implementor();
            Class<?> cur = Class.forName("info.kgeorgiy.ja.nebabin.implementor.Overridden.Base");
            System.out.println(Arrays.toString(cur.getMethods()));
            tmp.implement(Class.forName("info.kgeorgiy.ja.nebabin.implementor.CovariantReturns$Base"), Path.of("/Users/fireguy/IdeaProjects/java-techs/solutions/java-solutions"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ImplerException e) {
            System.err.println(e.getMessage());
        }
    }
}