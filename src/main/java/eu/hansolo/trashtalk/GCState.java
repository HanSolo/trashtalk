package eu.hansolo.trashtalk;

import eu.hansolo.toolbox.statemachine.State;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;


public enum GCState implements State {
    IDLE("Idle"),
    ALLOCATING("Allocating"),
    MARKING("Marking"),
    FREEING("Freeing"),
    UNMARKING("UnMarking"),
    PROMOTING("Promoting"),
    MAJOR_GC("Major GC"),
    COMPACTING("Compacting"),
    PROMOTING_ALL("Promoting all"),
    COPYING("Copying"),
    OUT_OF_MEMORY("Out of memory");

    static {
        IDLE.canTransitionTo(ALLOCATING);
        ALLOCATING.canTransitionTo(FREEING, MARKING, OUT_OF_MEMORY);
        MARKING.canTransitionTo(FREEING, COPYING, PROMOTING);
        COPYING.canTransitionTo(PROMOTING, FREEING);
        UNMARKING.canTransitionTo(FREEING);
        FREEING.canTransitionTo(ALLOCATING, PROMOTING, MAJOR_GC);
        PROMOTING.canTransitionTo(ALLOCATING);
        MAJOR_GC.canTransitionTo(OUT_OF_MEMORY, COMPACTING, PROMOTING_ALL, PROMOTING, ALLOCATING);
        COMPACTING.canTransitionTo(PROMOTING_ALL);
        PROMOTING_ALL.canTransitionTo(OUT_OF_MEMORY, ALLOCATING);
        OUT_OF_MEMORY.canTransitionTo(IDLE);
    }

    private final String name;
    private       Set    transitions;


    // ******************** Constructors **************************************
    GCState(final String name) {
        this.name = name;
    }


    // ******************** Private Methods ***********************************
    private void canTransitionTo(final GCState... transitions) {
        this.transitions = EnumSet.copyOf(Arrays.asList(transitions));
    }


    // ******************** Public Methods ************************************
    @Override public Set<State> getTransitions() {
        return this.transitions;
    }

    @Override public boolean canChangeTo(final State state) {
        return this.transitions.contains(state);
    }

    @Override public String getName() {
        return this.name;
    }
}
