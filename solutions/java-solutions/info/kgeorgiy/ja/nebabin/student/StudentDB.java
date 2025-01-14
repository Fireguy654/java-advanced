package info.kgeorgiy.ja.nebabin.student;

import info.kgeorgiy.ja.nebabin.student.shared.AdvancedQuery;
import info.kgeorgiy.ja.nebabin.student.shared.Group;
import info.kgeorgiy.ja.nebabin.student.shared.GroupName;
import info.kgeorgiy.ja.nebabin.student.shared.Student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements AdvancedQuery {
    private static final Comparator<Student> BY_ID = Comparator.comparingInt(Student::getId);


    private static final Comparator<Student> BY_F_NAME = Comparator.comparing(Student::getFirstName);
    

    private static final Comparator<Student> BY_NAME = Comparator.comparing(Student::getLastName)
            .thenComparing(Student::getFirstName).thenComparing(BY_ID.reversed());


    private static final Comparator<Map.Entry<String, Integer>> BY_F_NAME_SIZE = Map.Entry.comparingByValue();

    private static final Comparator<Map.Entry<String, Integer>> BY_TINIEST_F_NAME_SIZE = BY_F_NAME_SIZE
            .thenComparing(Map.Entry::getKey).reversed();
    
    private static final Comparator<Map.Entry<String, Integer>> BY_LARGEST_F_NAME_SIZE = BY_F_NAME_SIZE
            .thenComparing(Comparator.comparing(Map.Entry<String, Integer>::getKey).reversed());
    

    private static final Comparator<Map.Entry<GroupName, TreeSet<Student>>> BY_LARGEST_GROUP_UNIQUE = Comparator
            .comparingInt((Map.Entry<GroupName, TreeSet<Student>> me) -> me.getValue().size()).reversed()
            .thenComparing(Map.Entry::getKey).reversed();


    private static final Comparator<Map.Entry<GroupName, Long>> BY_LARGEST_GROUP_SIZE = Comparator
            .comparingLong(Map.Entry<GroupName, Long>::getValue).thenComparing(Map.Entry::getKey);
    
    
    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getListOfGroups(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getListOfGroups(students, this::sortStudentsById);
    }

    // :NOTE: Тоже объединить
    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getMaxGroupName(students, Collectors.counting(), BY_LARGEST_GROUP_SIZE);
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getMaxGroupName(
                students,
                Collectors.toCollection(() -> new TreeSet<>(BY_F_NAME)),
                BY_LARGEST_GROUP_UNIQUE
        );
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getListOf(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getListOf(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return getListOf(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getListOf(students, student -> student.getFirstName() + " " + student.getLastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return getStreamOf(students, Student::getFirstName).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream().max(BY_ID).map(Student::getFirstName).orElse("");
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return students.stream().sorted(BY_ID).toList();
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return orderedByName(students.stream());
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return orderedByName(filteredByMapEquals(students.stream(), Student::getFirstName, name));
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return orderedByName(filteredByMapEquals(students.stream(), Student::getLastName, name));
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return orderedByName(filteredByGroup(students.stream(), group));
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return filteredByGroup(students.stream(), group).collect(Collectors.toMap(
                Student::getLastName,
                Student::getFirstName,
                BinaryOperator.minBy(Comparator.naturalOrder())
        ));
    }

    // :NOTE: Объединить getLeastPopularName
    @Override
    public String getMostPopularName(Collection<Student> students) {
        return getMaxNameByComp(students, BY_LARGEST_F_NAME_SIZE);
    }

    @Override
    public String getLeastPopularName(Collection<Student> students) {
        return getMaxNameByComp(students, BY_TINIEST_F_NAME_SIZE);
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, int[] indices) {
        return getFirstNames(filterByInd(students, indices));
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, int[] indices) {
        return getLastNames(filterByInd(students, indices));
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, int[] indices) {
        return getGroups(filterByInd(students, indices));
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, int[] indices) {
        return getFullNames(filterByInd(students, indices));
    }

    private static List<Student> orderedByName(Stream<Student> stream) {
        return stream.sorted(BY_NAME).toList();
    }

    private static Stream<Student> filteredByGroup(Stream<Student> stream, GroupName group) {
        return filteredByMapEquals(stream, Student::getGroup, group);
    }

    private static <T> Stream<Student> filteredByMapEquals(Stream<Student> stream, Function<Student, T> mapper, T key) {
        return stream.filter(student -> key.equals(mapper.apply(student)));
    }

    private static <T> List<T> getListOf(List<Student> students, Function<Student, T> mapper) {
        return getStreamOf(students, mapper).toList();
    }

    private static <T> Stream<T> getStreamOf(List<Student> students, Function<Student, T> mapper) {
        return students.stream().map(mapper);
    }

    private static List<Group> getListOfGroups(Collection<Student> students, UnaryOperator<List<Student>> mapper) {
        return getGroupEntryStream(students, Collectors.toList())
                .map(me -> new Group(me.getKey(), mapper.apply(me.getValue()))).toList();
    }

    private static <T> Stream<Map.Entry<GroupName, T>> getGroupEntryStream(Collection<Student> students,
                                                                           Collector<Student, ?, T> collector) {
        return getMappedGroupEntryStream(students, collector, Student::getGroup);
    }

    private static List<Student> filterByInd(Collection<Student> students, int[] indices) {
        return Arrays.stream(indices).mapToObj(students.stream().toList()::get).toList();
    }

    private static <T> GroupName getMaxGroupName(Collection<Student> students,
                                                 Collector<Student, ?, T> collector,
                                                 Comparator<Map.Entry<GroupName, T>> comp) {
        return getMaxGroupKey(students, col -> getGroupEntryStream(col, collector), comp, null);
    }

    private static String getMaxNameByComp(Collection<Student> students, Comparator<Map.Entry<String, Integer>> comp) {
        return getMaxGroupKey(students,
                col -> getMappedGroupEntryStream(
                        col,
                        Collectors.collectingAndThen(
                                Collectors.mapping(Student::getGroup, Collectors.toSet()), Set::size
                        ),
                        Student::getFirstName
                ),
                comp,
                "");
    }

    private static <T, U> T getMaxGroupKey(Collection<Student> students,
                                           Function<Collection<Student>, Stream<Map.Entry<T, U>>> mapper,
                                           Comparator<Map.Entry<T, U>> comp,
                                           T def) {
        return mapper.apply(students).max(comp).map(Map.Entry::getKey).orElse(def);
    }

    private static <T, U> Stream<Map.Entry<U, T>> getMappedGroupEntryStream(Collection<Student> students,
                                                                            Collector<Student, ?, T> collector,
                                                                            Function<Student, U> groupBy) {
        return students.stream().collect(Collectors.groupingBy(groupBy, TreeMap::new, collector))
                .entrySet().stream();
    }
}
