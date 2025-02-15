package info.kgeorgiy.ja.nebabin.student.shared;

import java.util.List;
import java.util.Objects;

/**
 * Group of {@link Student students}.
 *
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public class Group {
    private final GroupName name;
    private final List<Student> students;

    public Group(final GroupName name, final List<Student> students) {
        this.name = Objects.requireNonNull(name);
        this.students = List.copyOf(students);
    }

    public GroupName getName() {
        return name;
    }

    public List<Student> getStudents() {
        return students;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Group) {
            final Group that = (Group) obj;
            return this.name.equals(that.name) && this.students.equals(that.students);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, students);
    }

    @Override
    public String toString() {
        return String.format("Group(%s)", name);
    }
}
