package eu.hansolo.trashtalk.references;

import java.util.Arrays;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        record Person(String name){
            @Override public String toString() { return name; }
        }

        Person p1 = new Person("Gerrit");
        Person p2 = new Person("Sandra");
        Person p3 = new Person("Lilli");
        Person p4 = new Person("Anton");

        List<Person> persons = Arrays.asList(p1, p2, p3, p4);

        p1 = null; // Set p1 to null -> not ready for GC because persons still has reference to p1 and is on the stack

        System.out.println(persons.get(0)); // Will print "Gerrit" even if the object is null

        persons = null; // Now all objects are ready for GC
    }
}
