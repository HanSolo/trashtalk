package eu.hansolo.trashtalk.copying;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.trashtalk.GCState;
import eu.hansolo.trashtalk.Helper;
import eu.hansolo.trashtalk.CellState;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


public class Collector {
    private ObjectProperty<GCState> state;
    private BooleanProperty         collecting;
    private BooleanProperty            toggleSurvivor;
    private List<AtomicReference<Obj>> markedInSurvivor;


    public Collector() {
        this.state            = new ObjectPropertyBase<>(GCState.IDLE) {
            @Override public Object getBean() { return Collector.this; }
            @Override public String getName() { return "state"; }
        };
        this.collecting       = new BooleanPropertyBase(false) {
            @Override public Object getBean() { return Collector.this; }
            @Override public String getName() { return "collecting"; }
        };
        this.toggleSurvivor   = new BooleanPropertyBase(false) {
            @Override public Object getBean() { return Collector.this; }
            @Override public String getName() { return "toggleSurvivor"; }
        };
        this.markedInSurvivor = new ArrayList<>();
    }


    public void collect(final List<Allocator> allocators, final ObservableMatrix<Obj> survivor1, final ObservableMatrix<Obj> survivor2, final Allocator allocator, final int amountToAllocate, AnimationTimer mutatorThread) {
        this.collecting.set(true);
        this.state.set(GCState.MARKING);
        this.toggleSurvivor.set(!this.toggleSurvivor.get());
        // Try to stop all allocators to reach safe point
        boolean safePoint = false;
        while (!safePoint) {
            allocators.forEach(allctr -> { if (allctr.canStop()) { allctr.stop(); } });
            safePoint = allocators.stream().filter(allctr -> allocator.isStopped()).count() == allocators.size();
            if (!safePoint) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {

                }
            }
        }

        if (this.toggleSurvivorProperty().get()) {
            // Mark all live objects and increase their survivedGarbageCollection counter in Survivor 1 Space
            System.out.println("Mark live objects and increase survived GC counter in Survivor 1");
            for (int y = 0 ; y < survivor1.getNoOfRows() ; y++) {
                for (int x = 0 ; x < survivor1.getNoOfCols() ; x++) {
                    Obj obj = survivor1.getItemAt(x, y);
                    if (CellState.REFERENCED == obj.getState()) {
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
                    if (CellState.REFERENCED == obj.getState()) {
                        obj.setMarked(true);
                        obj.incSurvivedGarbageCollections();
                    }
                }
            }
        }

        PauseTransition pause1 = Helper.pause(Constants.PAUSE_TIME);
        pause1.setOnFinished(e1 -> {
            System.out.println("Move all live objects into toSpace");

            if (this.toggleSurvivorProperty().get()) {
                this.markedInSurvivor.clear();
                this.markedInSurvivor.addAll(survivor1.stream().filter(ref -> ref.get().isMarked()).toList());
                PauseTransition pause2 = Helper.pause(Constants.PAUSE_TIME);
                pause2.setOnFinished(e2 -> {
                    this.state.set(GCState.COPYING);
                    int markedSurvivor1Counter = 0;
                    if (!this.markedInSurvivor.isEmpty()) {
                        System.out.println("markedInSurvivor1: " + markedInSurvivor.size());
                        for (int y = 0; y < survivor1.getNoOfRows(); y++) {
                            for (int x = 0; x < survivor1.getNoOfCols(); x++) {
                                if (markedSurvivor1Counter == markedInSurvivor.size()) { break; }
                                survivor2.getItemAt(x, y).update(markedInSurvivor.get(markedSurvivor1Counter).get());
                                markedSurvivor1Counter++;
                            }
                        }
                        markedInSurvivor.forEach(ref -> ref.get().setState(CellState.FREE));
                    }

                    PauseTransition pause3 = Helper.pause(Constants.PAUSE_TIME);
                    pause3.setOnFinished(e3 -> {
                        this.state.set(GCState.FREEING);
                        survivor1.stream().forEach(obj -> obj.get().setState(CellState.FREE));

                        PauseTransition pause4 = Helper.pause(Constants.PAUSE_TIME);
                        pause4.setOnFinished(e4 -> {
                            // Resume all allocators
                            System.out.println("Resume allocators");
                            allocators.forEach(allctr -> allocator.resume());
                            this.collecting.set(false);
                            this.state.set(GCState.ALLOCATING);
                        });
                        pause4.play();
                    });
                    pause3.play();
                });
                pause2.play();
            } else {
                this.markedInSurvivor.clear();
                this.markedInSurvivor.addAll(survivor2.stream().filter(ref -> ref.get().isMarked()).toList());
                PauseTransition pause2 = Helper.pause(Constants.PAUSE_TIME);
                pause2.setOnFinished(e2 -> {
                    this.state.set(GCState.COPYING);
                    int markedSurvivor2Counter = 0;
                    if (!markedInSurvivor.isEmpty()) {
                        System.out.println("markedInSurvivor2: " + markedInSurvivor.size());
                        for (int y = 0; y < survivor2.getNoOfRows(); y++) {
                            for (int x = 0; x < survivor2.getNoOfCols(); x++) {
                                if (markedSurvivor2Counter == markedInSurvivor.size()) { break; }
                                survivor1.getItemAt(x, y).update(markedInSurvivor.get(markedSurvivor2Counter).get());
                                markedSurvivor2Counter++;
                            }
                        }
                        markedInSurvivor.forEach(ref -> ref.get().setState(CellState.FREE));
                    }

                    PauseTransition pause3 = Helper.pause(Constants.PAUSE_TIME);
                    pause3.setOnFinished(e3 -> {
                        this.state.set(GCState.FREEING);
                        survivor2.stream().forEach(obj -> obj.get().setState(CellState.FREE));

                        PauseTransition pause4 = Helper.pause(Constants.PAUSE_TIME);
                        pause4.setOnFinished(e4 -> {
                            // Resume all allocators
                            System.out.println("Resume allocators");
                            allocators.forEach(allctr -> allocator.resume());
                            this.collecting.set(false);
                            this.state.set(GCState.ALLOCATING);
                        });
                        pause4.play();
                    });
                    pause3.play();
                });
                pause2.play();
            }
        });
        pause1.play();
    }

    public BooleanProperty toggleSurvivorProperty() { return this.toggleSurvivor; }

    public GCState getState()                      { return this.state.get(); }
    public void setState(final GCState GCState)     { this.state.set(GCState); }
    public ObjectProperty<GCState> stateProperty() { return this.state; }

    public boolean isCollecting() { return this.collecting.get(); }
    public ReadOnlyBooleanProperty collectingProperty() { return this.collecting; }
}
