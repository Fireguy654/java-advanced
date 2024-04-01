/**
 * Defines info of module, which contains solutions of Java Advanced course
 *
 * @author Nebabin Nikita
 */
module java.techs {
    requires java.compiler;
    requires info.kgeorgiy.java.advanced.student;
    requires transitive info.kgeorgiy.java.advanced.implementor;

    exports info.kgeorgiy.ja.nebabin.walk;
    exports info.kgeorgiy.ja.nebabin.arrayset;
    exports info.kgeorgiy.ja.nebabin.student;
    exports info.kgeorgiy.ja.nebabin.implementor;
}