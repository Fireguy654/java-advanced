/**
 * Defines info of module, which contains solutions of Java Advanced course
 *
 * @author Nebabin Nikita
 */
module java.techs {
    requires java.compiler;
    requires jsoup;

    exports info.kgeorgiy.ja.nebabin.walk;
    exports info.kgeorgiy.ja.nebabin.arrayset;
    exports info.kgeorgiy.ja.nebabin.student;
    exports info.kgeorgiy.ja.nebabin.implementor;
    exports info.kgeorgiy.ja.nebabin.iterative;
    exports info.kgeorgiy.ja.nebabin.crawler;
    exports info.kgeorgiy.ja.nebabin.hello;
    exports info.kgeorgiy.ja.nebabin.crawler.shared;
    exports info.kgeorgiy.ja.nebabin.hello.shared;
    exports info.kgeorgiy.ja.nebabin.implementor.shared;
    exports info.kgeorgiy.ja.nebabin.iterative.shared;
    exports info.kgeorgiy.ja.nebabin.student.shared;
}