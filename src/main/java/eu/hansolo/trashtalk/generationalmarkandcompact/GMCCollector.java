package eu.hansolo.trashtalk.generationalmarkandcompact;
import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.toolbox.statemachine.State;
import eu.hansolo.toolbox.statemachine.StateChangeException;
import eu.hansolo.toolbox.statemachine.StateMachine;
import eu.hansolo.trashtalk.CellState;
import eu.hansolo.trashtalk.GCState;
import eu.hansolo.trashtalk.Helper;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static eu.hansolo.trashtalk.generationalmarkandcompact.GMCConstants.LIFE_TIMES_IN_MS;
import static eu.hansolo.trashtalk.generationalmarkandcompact.GMCConstants.RND;


public class GMCCollector {
    private Allocator                  allocator;
    private BooleanProperty            collecting;
    private BooleanProperty            toggleSurvivor;
    private List<AtomicReference<Obj>> markedInEden;
    private StateMachine<GCState>      stateMachine;
    private ObjectProperty<GCState>    state;
    private ObjectProperty<GCState>    tenuredState;
    private long                       lastMutatorCall;
    private long                       lastAgingCall;
    private long                       lastCollectorCall;
    private AtomicBoolean              mutatorRunning;
    private Thread                     mutatorThread;
    private ObservableMatrix<Obj>      eden;
    private ObservableMatrix<Obj>      survivor1;
    private ObservableMatrix<Obj>      survivor2;
    private ObservableMatrix<Obj>      tenured;


    // ******************** Constructor ***************************************
    public GMCCollector() throws StateChangeException {
        this.stateMachine      = new StateMachine<>() {
            private eu.hansolo.toolbox.properties.ObjectProperty<GCState> state = new eu.hansolo.toolbox.properties.ObjectProperty<>(GCState.IDLE);

            // ******************** Public Methods ****************************
            @Override public State getState() { return this.state.get(); }

            @Override public void setState(final GCState state) throws StateChangeException {
                if (this.state.get().canChangeTo(state)) {
                    // Pause before each state change
                    try {
                        Thread.sleep(GMCConstants.PAUSE_TIME);
                    } catch (InterruptedException e) {}

                    this.state.set(state);
                } else {
                    throw new StateChangeException("Not allowed to change from " + this.state.get().getName() + " to " + state.getName());
                }
            }

            @Override public eu.hansolo.toolbox.properties.ObjectProperty<GCState> stateProperty() { return state; }
        };
        this.state             = new ObjectPropertyBase<>(GCState.IDLE) {
            @Override public Object getBean() { return GMCCollector.this; }
            @Override public String getName() { return "state"; }
        };
        this.tenuredState      = new ObjectPropertyBase<>(GCState.IDLE) {
            @Override public Object getBean() { return GMCCollector.this; }
            @Override public String getName() { return "tenuredState"; }
        };
        this.allocator         = new Allocator();
        this.collecting        = new BooleanPropertyBase(false) {
            @Override public Object getBean() { return GMCCollector.this; }
            @Override public String getName() { return "collecting"; }
        };
        this.toggleSurvivor    = new BooleanPropertyBase(false) {
            @Override public Object getBean() { return GMCCollector.this; }
            @Override public String getName() { return "toggleSurvivor"; }
        };
        this.markedInEden      = new ArrayList<>();
        this.lastMutatorCall   = System.nanoTime();
        this.lastAgingCall     = System.nanoTime();
        this.lastCollectorCall = System.nanoTime();
        this.mutatorRunning    = new AtomicBoolean(false);
        this.mutatorThread     = Thread.ofVirtual().name("Mutator").unstarted(() -> {
            while (mutatorRunning.get()) {
                final long now = System.nanoTime();
                if (now > lastMutatorCall + GMCConstants.ALLOCATION_INTERVAL) {
                    // Allocate memory and if no memory available run a garbage collection
                    int amountToAllocate = GMCConstants.SIZES[RND.nextInt(0, GMCConstants.SIZES.length)];
                    if (!this.collecting.get() && !this.allocator.allocate(eden, amountToAllocate)) {
                        System.out.println("Garbage Collection (after " + ((System.nanoTime() - this.lastCollectorCall) / 1_000_000) + " ms)");
                        this.collecting.set(true);
                        setState(GCState.MARKING);
                    }
                    this.lastMutatorCall = now;
                }

                // Call all objects to reduce their lifespan
                if (!this.collecting.get() && now > this.lastAgingCall + GMCConstants.AGING_INTERVAL) {
                    eden.stream().filter(ref      -> ref.get().isReferenced()).forEach(ref -> ref.get().decLifespan());
                    survivor1.stream().filter(ref -> ref.get().isReferenced()).forEach(ref -> ref.get().decLifespan());
                    survivor2.stream().filter(ref -> ref.get().isReferenced()).forEach(ref -> ref.get().decLifespan());
                    tenured.stream().filter(ref   -> ref.get().isReferenced()).forEach(ref -> ref.get().decLifespan());
                    this.lastAgingCall = now;
                }

                try { Thread.sleep(10); } catch (InterruptedException e) { }
            }
        });
        this.eden              = new ObservableMatrix<>(Obj.class, GMCConstants.NO_OF_ROWS_EDEN, GMCConstants.NO_OF_COLS_EDEN);
        this.survivor1         = new ObservableMatrix<>(Obj.class, GMCConstants.NO_OF_ROWS_SURVIVER, GMCConstants.NO_OF_COLS_SURVIVER);
        this.survivor2         = new ObservableMatrix<>(Obj.class, GMCConstants.NO_OF_ROWS_SURVIVER, GMCConstants.NO_OF_COLS_SURVIVER);
        this.tenured           = new ObservableMatrix<>(Obj.class, GMCConstants.NO_OF_ROWS_TENURED, GMCConstants.NO_OF_COLS_TENURED);


        // Setup StateMachine and Memory Areas
        for (int y = 0; y < GMCConstants.NO_OF_COLS_EDEN; y++) {
            for (int x = 0; x < GMCConstants.NO_OF_ROWS_EDEN; x++) {
                this.eden.setItemAt(x, y, new Obj());
            }
        }
        for (int y = 0; y < GMCConstants.NO_OF_COLS_SURVIVER; y++) {
            for (int x = 0; x < GMCConstants.NO_OF_ROWS_SURVIVER; x++) {
                this.survivor1.setItemAt(x, y, new Obj());
                this.survivor2.setItemAt(x, y, new Obj());
            }
        }
        for (int y = 0; y < GMCConstants.NO_OF_COLS_TENURED; y++) {
            for (int x = 0; x < GMCConstants.NO_OF_ROWS_TENURED; x++) {
                this.tenured.setItemAt(x, y, new Obj());
            }
        }

        // Register listeners
        registerListeners();
    }


    // ******************** Private Methods ***********************************
    private void registerListeners() {
        this.stateMachine.stateProperty().addOnChange(evt -> {
            Platform.runLater(() -> this.state.set(evt.getValue()));
            System.out.println(evt.getValue().getName());
            switch (evt.getValue()) {
                case IDLE          -> {

                }
                case ALLOCATING    -> {
                    this.collecting.set(false);
                    if (this.allocator.isStopped()) {
                        this.allocator.resume();
                    }
                }
                case MARKING       -> {
                    this.lastCollectorCall = System.nanoTime();
                    this.collecting.set(true);
                    this.toggleSurvivor.set(!this.toggleSurvivor.get());
                    this.allocator.stop();
                    /*
                    while (!allocator.stopped) {
                        if (allocator.canStop()) {
                            allocator.stop();
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {

                        }
                    }
                    */

                    if (this.toggleSurvivorProperty().get()) {
                        // Mark all live objects and increase their survivedGarbageCollection counter in Survivor 1 Space
                        System.out.println("Mark live objects and increase survived GC counter in Survivor 1");
                        for (int y = 0 ; y < survivor1.getNoOfRows() ; y++) {
                            for (int x = 0 ; x < survivor1.getNoOfCols() ; x++) {
                                Obj obj = survivor1.getItemAt(x, y);
                                if (obj.isReferenced()) {
                                    obj.setMarked(true);
                                    obj.incSurvivedGarbageCollections();
                                }
                            }
                        }
                    } else {
                        // Mark all live objects and increase their survivedGarbageCollection counter in Survivor 2 Space
                        System.out.println("Mark live objects and increase survived GC counter in Survivor 2");
                        for (int y = 0 ; y < survivor2.getNoOfRows() ; y++) {
                            for (int x = 0 ; x < survivor2.getNoOfCols() ; x++) {
                                Obj obj = survivor2.getItemAt(x, y);
                                if (obj.isReferenced()) {
                                    obj.setMarked(true);
                                    obj.incSurvivedGarbageCollections();
                                }
                            }
                        }
                    }

                    // Mark all live objects and increase their survivedGarbageCollection counter in Eden Space
                    this.markedInEden.clear();
                    for (int y = 0 ; y < eden.getNoOfRows() ; y++) {
                        for (int x = 0 ; x < eden.getNoOfCols() ; x++) {
                            Obj obj = eden.getItemAt(x, y);
                            if (obj.isReferenced()) {
                                obj.setMarked(true);
                                obj.incSurvivedGarbageCollections();
                                markedInEden.add(new AtomicReference<>(obj));
                            }
                        }
                    }
                    setState(GCState.FREEING);
                }
                case FREEING       -> {
                    if (this.toggleSurvivorProperty().get()) {
                        survivor1.stream().filter(obj -> !obj.get().isMarked()).forEach(obj -> obj.get().setState(CellState.FREE));
                    } else {
                        survivor2.stream().filter(obj -> !obj.get().isMarked()).forEach(obj -> obj.get().setState(CellState.FREE));
                    }
                    eden.stream().filter(obj -> !obj.get().isMarked()).forEach(obj -> obj.get().setState(CellState.FREE));
                    setState(GCState.UNMARKING);
                }
                case UNMARKING     -> {
                    eden.stream().filter(ref -> ref.get().isMarked()).forEach(ref -> ref.get().setMarked(false));
                    if (this.toggleSurvivorProperty().get()) {
                        survivor1.stream().filter(ref -> ref.get().isMarked()).forEach(ref -> ref.get().setMarked(false));
                    } else {
                        survivor2.stream().filter(ref -> ref.get().isMarked()).forEach(ref -> ref.get().setMarked(false));
                    }

                    // Decide if promoting is possible and either promote or start major gc
                    long availableSpaceInTenured = tenured.stream().filter(ref -> ref.get().isFree()).count();

                    if (this.toggleSurvivorProperty().get()) {
                        List<AtomicReference<Obj>> toPromoteInSurvivor1 = survivor1.stream().filter(ref -> ref.get().getSurvivedGarbageCollections() >= GMCConstants.PROMOTION_THRESHOLD).toList();
                        // Check if major GC is needed (Tenured run out of space)
                        if (toPromoteInSurvivor1.size() > availableSpaceInTenured) {
                            setState(GCState.MAJOR_GC);
                        } else {
                            if (toPromoteInSurvivor1.isEmpty()) {
                                setState(GCState.COPYING);
                            } else {
                                setState(GCState.PROMOTING);
                            }
                        }
                    } else {
                        List<AtomicReference<Obj>> toPromoteInSurvivor2 = survivor2.stream().filter(ref -> ref.get().getSurvivedGarbageCollections() >= GMCConstants.PROMOTION_THRESHOLD).toList();
                        // Check if major GC is needed (Tenured run out of space)
                        if (toPromoteInSurvivor2.size() > availableSpaceInTenured) {
                            setState(GCState.MAJOR_GC);
                        } else {
                            if (toPromoteInSurvivor2.isEmpty()) {
                                setState(GCState.COPYING);
                            } else {
                                setState(GCState.PROMOTING);
                            }
                        }
                    }
                }
                case MAJOR_GC      -> {
                    System.out.println("Mark live objects in Tenured");
                    setTenuredState(GCState.MARKING);
                    for (int y = 0 ; y < tenured.getNoOfRows() ; y++) {
                        for (int x = 0 ; x < tenured.getNoOfCols() ; x++) {
                            Obj obj = tenured.getItemAt(x, y);
                            if (obj.isReferenced()) {
                                obj.setMarked(true);
                            }
                        }
                    }
                    try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}

                    System.out.println("Free dead objects in Tenured");
                    setTenuredState(GCState.FREEING);
                    tenured.stream().filter(obj -> obj.get().isDeReferenced()).forEach(obj -> obj.get().setState(CellState.FREE));
                    try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}

                    System.out.println("Unmark live objects in Tenured");
                    setTenuredState(GCState.UNMARKING);
                    tenured.stream().filter(obj -> obj.get().isMarked()).forEach(obj -> obj.get().setMarked(false));
                    try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}
                    setState(GCState.COMPACTING);
                }
                case PROMOTING     -> {
                    if (this.toggleSurvivorProperty().get()) {
                        List<AtomicReference<Obj>> toPromoteInSurvivor1 = survivor1.stream().filter(ref -> ref.get().getSurvivedGarbageCollections() >= GMCConstants.PROMOTION_THRESHOLD).toList();
                        // Move promoted objects from Survivor 1 to Tenured
                        int promoteSurvivor1Counter = 0;
                        if (!toPromoteInSurvivor1.isEmpty()) {
                            for (int y = 0; y < tenured.getNoOfRows(); y++) {
                                for (int x = 0; x < tenured.getNoOfCols(); x++) {
                                    if (promoteSurvivor1Counter == toPromoteInSurvivor1.size()) { break; }
                                    if (tenured.getItemAt(x, y).isFree()) {
                                        tenured.getItemAt(x, y).update(toPromoteInSurvivor1.get(promoteSurvivor1Counter).get());
                                        tenured.getItemAt(x, y).setSurvivedGarbageCollections(0);
                                        promoteSurvivor1Counter++;
                                    }
                                }
                            }
                            toPromoteInSurvivor1.forEach(ref -> ref.get().setState(CellState.FREE));
                        }
                    } else {
                        List<AtomicReference<Obj>> toPromoteInSurvivor2 = survivor2.stream().filter(ref -> ref.get().getSurvivedGarbageCollections() >= GMCConstants.PROMOTION_THRESHOLD).toList();
                        // Move promoted objects from Survivor 2 to Tenured
                        int promoteSurvivor2Counter = 0;
                        if (!toPromoteInSurvivor2.isEmpty()) {
                            for (int y = 0; y < tenured.getNoOfRows(); y++) {
                                for (int x = 0; x < tenured.getNoOfCols(); x++) {
                                    if (promoteSurvivor2Counter == toPromoteInSurvivor2.size()) { break; }
                                    if (tenured.getItemAt(x, y).isFree()) {
                                        tenured.getItemAt(x, y).update(toPromoteInSurvivor2.get(promoteSurvivor2Counter).get());
                                        tenured.getItemAt(x, y).setSurvivedGarbageCollections(0);
                                        promoteSurvivor2Counter++;
                                    }
                                }
                            }
                            toPromoteInSurvivor2.forEach(ref -> ref.get().setState(CellState.FREE));
                        }
                    }
                    setState(GCState.COPYING);
                }
                case COMPACTING    -> {
                    System.out.println("Compact Tenured");
                    setTenuredState(GCState.COMPACTING);
                    List<AtomicReference<Obj>> referencedInTenured = tenured.stream().filter(ref -> ref.get().isReferenced()).toList();
                    int referencedInTenuredCounter = 0;
                    if (!referencedInTenured.isEmpty()) {
                        for (int y = 0; y < tenured.getNoOfRows(); y++) {
                            for (int x = 0; x < tenured.getNoOfCols(); x++) {
                                if (referencedInTenuredCounter < referencedInTenured.size()) {
                                    tenured.getItemAt(x, y).update(referencedInTenured.get(referencedInTenuredCounter).get());
                                    referencedInTenuredCounter++;
                                } else {
                                    tenured.getItemAt(x, y).setState(CellState.FREE);
                                }
                            }
                        }
                    }
                    try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}
                    setState(GCState.PROMOTING_ALL);
                }
                case PROMOTING_ALL -> {
                    // Promote referenced objects from survivor spaces to tenured space
                    if (this.toggleSurvivorProperty().get()) {
                        long                       availableSpaceInTenured = tenured.stream().filter(ref -> ref.get().isFree()).count();
                        List<AtomicReference<Obj>> referencedInSurvivor1   = survivor1.stream().filter(ref -> ref.get().isReferenced()).toList();
                        if (referencedInSurvivor1.size() > availableSpaceInTenured) {
                            System.out.println("Out of memory (not able to allocate " + referencedInSurvivor1.size() + " cells in Tenured)");
                            setState(GCState.OUT_OF_MEMORY);
                        } else {
                            // Survivor 1
                            System.out.println("Promote all objects from Survivor 1 to Tenured");
                            int referencedInSurvivorCounter = 0;
                            for (int y = 0 ; y < tenured.getNoOfRows() ; y++) {
                                for (int x = 0 ; x < tenured.getNoOfCols() ; x++) {
                                    if (referencedInSurvivorCounter == referencedInSurvivor1.size()) { break; }
                                    if (tenured.getItemAt(x, y).isFree()) {
                                        tenured.getItemAt(x, y).update(referencedInSurvivor1.get(referencedInSurvivorCounter).get());
                                        tenured.getItemAt(x, y).setSurvivedGarbageCollections(0);
                                        referencedInSurvivorCounter++;
                                    }
                                }
                            }
                            referencedInSurvivor1.forEach(ref -> ref.get().setState(CellState.FREE));
                            try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}
                        }
                    } else {
                        long                       availableSpaceInTenured = tenured.stream().filter(ref -> ref.get().isFree()).count();
                        List<AtomicReference<Obj>> referencedInSurvivor2   = survivor2.stream().filter(ref -> ref.get().isReferenced()).toList();
                        if (referencedInSurvivor2.size() > availableSpaceInTenured) {
                            System.out.println("Out of memory (not able to allocate " + referencedInSurvivor2.size() + " cells in Tenured)");
                            setState(GCState.OUT_OF_MEMORY);
                        } else {
                            // Survivor 2
                            System.out.println("Promote all objects from Survivor 2 to Tenured");
                            int referencedInSurvivorCounter = 0;
                            for (int y = 0 ; y < tenured.getNoOfRows() ; y++) {
                                for (int x = 0 ; x < tenured.getNoOfCols() ; x++) {
                                    if (referencedInSurvivorCounter == referencedInSurvivor2.size()) { break; }
                                    if (tenured.getItemAt(x, y).isFree()) {
                                        tenured.getItemAt(x, y).update(referencedInSurvivor2.get(referencedInSurvivorCounter).get());
                                        tenured.getItemAt(x, y).setSurvivedGarbageCollections(0);
                                        referencedInSurvivorCounter++;
                                    }
                                }
                            }
                            referencedInSurvivor2.forEach(ref -> ref.get().setState(CellState.FREE));
                            try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) {}
                        }
                    }
                    // Promote all referenced objects from Eden to Tenured
                    List<AtomicReference<Obj>> referencedInEden        = eden.stream().filter(ref -> ref.get().isReferenced()).toList();
                    long                       availableSpaceInTenured = tenured.stream().filter(ref -> ref.get().isFree()).count();
                    if (referencedInEden.size() > availableSpaceInTenured) {
                        System.out.println("Out of memory (not able to allocate " + referencedInEden.size() + " cells in Tenured)");
                        setState(GCState.OUT_OF_MEMORY);
                    } else {
                        // Promote all objects from Eden to Tenured
                        System.out.println("Promote all objects from Eden to Tenured");
                        int referencedInEdenCounter = 0;
                        for (int y = 0 ; y < tenured.getNoOfRows() ; y++) {
                            for (int x = 0 ; x < tenured.getNoOfCols() ; x++) {
                                if (referencedInEdenCounter == referencedInEden.size()) { break; }
                                if (tenured.getItemAt(x, y).isFree()) {
                                    tenured.getItemAt(x, y).update(referencedInEden.get(referencedInEdenCounter).get());
                                    tenured.getItemAt(x, y).setSurvivedGarbageCollections(0);
                                    referencedInEdenCounter++;
                                }
                            }
                        }
                        referencedInEden.forEach(ref -> ref.get().setState(CellState.FREE));
                    }
                    setState(GCState.ALLOCATING);
                }
                case COPYING       -> {
                    if (this.toggleSurvivorProperty().get()) {
                        List<AtomicReference<Obj>> referencedInSurvivor1      = survivor1.stream().filter(ref -> ref.get().isReferenced()).filter(ref -> ref.get().getSurvivedGarbageCollections() < GMCConstants.PROMOTION_THRESHOLD).toList();
                        int                        referencedSurvivor1Counter = 0;
                        if (!referencedInSurvivor1.isEmpty()) {
                            for (int y = 0; y < survivor1.getNoOfRows(); y++) {
                                for (int x = 0; x < survivor1.getNoOfCols(); x++) {
                                    if (referencedSurvivor1Counter == referencedInSurvivor1.size()) { break; }
                                    survivor2.getItemAt(x, y).update(referencedInSurvivor1.get(referencedSurvivor1Counter).get());
                                    referencedSurvivor1Counter++;
                                }
                            }
                            referencedInSurvivor1.forEach(ref -> ref.get().setState(CellState.FREE));
                        }

                        // Check for available space in survivor 2
                        long availableSpaceInSurvivor2 = survivor2.stream().filter(ref -> ref.get().isFree()).count();
                        if (markedInEden.size() > availableSpaceInSurvivor2) {
                            System.out.println("Out of memory (not able to allocate " + markedInEden.size() + " cells in Survivor 2)");
                            setState(GCState.OUT_OF_MEMORY);
                        }

                        // Pause between copy from Survivor 1 to Survivor 2 and from Eden to Survivor 2
                        try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) { }

                        int markedEdenCounter = 0;
                        if (!markedInEden.isEmpty()) {
                            for (int y = 0; y < survivor2.getNoOfRows(); y++) {
                                for (int x = 0; x < survivor2.getNoOfCols(); x++) {
                                    if (markedEdenCounter == markedInEden.size()) { break; }
                                    if (survivor2.getItemAt(x, y).isFree()) {
                                        survivor2.getItemAt(x, y).update(markedInEden.get(markedEdenCounter).get());
                                        markedEdenCounter++;
                                    }
                                }
                            }
                            markedInEden.forEach(ref -> ref.get().setState(CellState.FREE));
                        }
                        survivor2.stream().filter(ref -> ref.get().isMarked()).forEach(ref -> ref.get().setMarked(false));

                        setState(GCState.ALLOCATING);
                    } else {
                        List<AtomicReference<Obj>> referencedInSurvivor2      = survivor2.stream().filter(ref -> ref.get().isReferenced()).filter(ref -> ref.get().getSurvivedGarbageCollections() < GMCConstants.PROMOTION_THRESHOLD).toList();
                        int                        referencedSurvivor2Counter = 0;
                        if (!referencedInSurvivor2.isEmpty()) {
                            for (int y = 0; y < survivor2.getNoOfRows(); y++) {
                                for (int x = 0; x < survivor2.getNoOfCols(); x++) {
                                    if (referencedSurvivor2Counter == referencedInSurvivor2.size()) { break; }
                                    survivor1.getItemAt(x, y).update(referencedInSurvivor2.get(referencedSurvivor2Counter).get());
                                    referencedSurvivor2Counter++;
                                }
                            }
                            referencedInSurvivor2.forEach(ref -> ref.get().setState(CellState.FREE));
                        }

                        // Check for available space in survivor 1
                        long availableSpaceInSurvivor1 = survivor1.stream().filter(ref -> ref.get().isFree()).count();
                        if (markedInEden.size() > availableSpaceInSurvivor1) {
                            System.out.println("Out of memory (not able to allocate " + markedInEden.size() + " cells in Survivor 1)");
                            setState(GCState.OUT_OF_MEMORY);
                        }

                        // Pause between copy from Survivor 2 to Survivor 1 and from Eden to Survivor 1
                        try { Thread.sleep(GMCConstants.PAUSE_TIME); } catch (InterruptedException e) { }

                        int markedEdenCounter = 0;
                        if (!markedInEden.isEmpty()) {
                            for (int y = 0; y < survivor1.getNoOfRows(); y++) {
                                for (int x = 0; x < survivor1.getNoOfCols(); x++) {
                                    if (markedEdenCounter == markedInEden.size()) { break; }
                                    if (survivor1.getItemAt(x, y).isFree()) {
                                        survivor1.getItemAt(x, y).update(markedInEden.get(markedEdenCounter).get());
                                        markedEdenCounter++;
                                    }
                                }
                            }
                            markedInEden.forEach(ref -> ref.get().setState(CellState.FREE));
                        }
                        survivor1.stream().filter(ref -> ref.get().isMarked()).forEach(ref -> ref.get().setMarked(false));

                        setState(GCState.ALLOCATING);
                    }
                }
                case OUT_OF_MEMORY -> {
                    mutatorRunning.set(false);
                    setState(GCState.IDLE);
                }
            }
        });
    }


    // ******************** Public Methods ************************************
    public BooleanProperty toggleSurvivorProperty() { return this.toggleSurvivor; }

    public GCState getState() { return (GCState) this.stateMachine.getState(); }
    private void setState(final GCState state) {
        try {
            this.stateMachine.setState(state);
        } catch (StateChangeException e) {
            System.out.println(e.getError());
        }
    }
    public ObjectProperty<GCState> stateProperty() { return this.state; }

    public GCState getTenuredState() { return this.tenuredState.get(); }
    private void setTenuredState(final GCState state) {
        Platform.runLater(() -> this.tenuredState.set(state));
    }
    public ObjectProperty<GCState> tenuredStateProperty() { return this.tenuredState; }

    public boolean isCollecting()                       { return this.collecting.get(); }
    public ReadOnlyBooleanProperty collectingProperty() { return this.collecting; }

    public ObservableMatrix<Obj> getEden() { return this.eden; }

    public ObservableMatrix<Obj> getSurvivor1() { return this.survivor1; }

    public ObservableMatrix<Obj> getSurvivor2() { return this.survivor2; }

    public ObservableMatrix<Obj> getTenured() { return this.tenured; }

    public ReadOnlyBooleanProperty getToggleSurvivorProperty() { return toggleSurvivor; }

    public void start() {
        if (this.mutatorRunning.get()) { return; }
        this.mutatorRunning.set(true);
        this.mutatorThread.start();
        setState(GCState.ALLOCATING);
    }


    // ******************** Inner Classes *************************************
    public class Allocator {
        private boolean allocating;
        private boolean stopped;


        // ******************** Constructors **********************************
        public Allocator() {
            this.allocating = false;
            this.stopped    = false;
        }


        // ******************** Public Methods ********************************
        public boolean allocate(final ObservableMatrix<Obj> space, final int amount) {
            boolean result = false;
            if (this.stopped) { return result; }
            this.allocating = true;
            record AllocatableObj(int x, int y, Obj block){};
            List<AllocatableObj> allocatableObjects = new ArrayList<>();
            int                  objCounter         = 0;

            for (int y = 0 ; y < space.getNoOfRows() ; y++) {
                for (int x = 0 ; x < space.getNoOfCols() ; x++) {
                    Obj obj = space.getItemAt(x, y);
                    if (objCounter < amount && obj.isFree()) {
                        objCounter++;
                        allocatableObjects.add(new AllocatableObj(x, y, obj));
                        if (objCounter == amount) {
                            result = true;
                            break;
                        }
                    } else {
                        if (objCounter < amount) {
                            objCounter = 0;
                            allocatableObjects.clear();
                        }
                    }
                }
            }
            allocatableObjects.forEach(allocatableObj -> {
                space.getItemAt(allocatableObj.x(), allocatableObj.y()).setState(CellState.REFERENCED);
                space.getItemAt(allocatableObj.x(), allocatableObj.y()).setLifeSpanInMilliSeconds(LIFE_TIMES_IN_MS[RND.nextInt(0, LIFE_TIMES_IN_MS.length)]);
            });
            this.allocating = false;
            return result;
        }

        public boolean allocate1(final ObservableMatrix<Obj> space, final int amount) {
            boolean result = false;
            if (this.stopped) { return result; }
            this.allocating = true;
            Callable<Boolean> callable = () -> {
                boolean res = false;
                record AllocatableObj(int x, int y, Obj block){};
                List<AllocatableObj> allocatableObjects = new ArrayList<>();
                int                  objCounter         = 0;

                for (int y = 0 ; y < space.getNoOfRows() ; y++) {
                    for (int x = 0 ; x < space.getNoOfCols() ; x++) {
                        Obj obj = space.getItemAt(x, y);
                        if (objCounter < amount && obj.isFree()) {
                            objCounter++;
                            allocatableObjects.add(new AllocatableObj(x, y, obj));
                            if (objCounter == amount) {
                                res = true;
                                break;
                            }
                        } else {
                            if (objCounter < amount) {
                                objCounter = 0;
                                allocatableObjects.clear();
                            }
                        }
                    }
                }
                allocatableObjects.forEach(allocatableObj -> {
                    space.getItemAt(allocatableObj.x(), allocatableObj.y()).setState(CellState.REFERENCED);
                    space.getItemAt(allocatableObj.x(), allocatableObj.y()).setLifeSpanInMilliSeconds(LIFE_TIMES_IN_MS[RND.nextInt(0, LIFE_TIMES_IN_MS.length)]);
                });
                return res;
            };
            this.allocating = false;
            return Helper.runAndWait(callable);
        }

        public boolean canStop() { return !this.allocating; }

        public boolean isStopped() { return this.stopped; }

        public boolean stop() {
            if (this.allocating) {
                return false;
            } else {
                this.stopped = true;
                return true;
            }
        }

        public void resume() {
            if (this.allocating) { return; }
            this.stopped = false;
        }
    }
}