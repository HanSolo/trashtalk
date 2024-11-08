package eu.hansolo.trashtalk.markandsweep;

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

import java.util.List;


public class Collector {
    private ObjectProperty<GCState> state;
    private BooleanProperty         collecting;


    public Collector() {
        this.state      = new ObjectPropertyBase<>(GCState.IDLE) {
            @Override public Object getBean() { return Collector.this; }
            @Override public String getName() { return "state"; }
        };
        this.collecting = new BooleanPropertyBase(false) {
            @Override public Object getBean() { return Collector.this; }
            @Override public String getName() { return "collecting"; }
        };
    }


    public void collect(final List<Allocator> allocators, final ObservableMatrix<Obj> heap, final Allocator allocator, final int amountToAllocate, AnimationTimer mutatorThread) {
        this.collecting.set(true);
        this.state.set(GCState.MARKING);
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

        // Mark all live objects and increase their survivedGarbageCollection counter
        System.out.println("Mark live objects and increase survived GC counter");
        for (int y = 0 ; y < heap.getNoOfRows() ; y++) {
            for (int x = 0 ; x < heap.getNoOfCols() ; x++) {
                Obj obj = heap.getItemAt(x, y);
                if (CellState.REFERENCED == obj.getState()) {
                    obj.setMarked(true);
                    obj.incSurvivedGarbageCollections();
                }
            }
        }

        PauseTransition pause1 = Helper.pause(Constants.PAUSE_TIME);
        pause1.setOnFinished(e1 -> {
            // Free all dead objects
            System.out.println("Free dead objects");
            this.state.set(GCState.FREEING);
            heap.stream().filter(obj -> !obj.get().isMarked()).forEach(obj -> obj.get().setState(CellState.FREE));
            PauseTransition pause2 = Helper.pause(Constants.PAUSE_TIME);
            pause2.setOnFinished(e2 -> {
                System.out.println("Unmark live objects");
                this.state.set(GCState.UNMARKING);
                heap.stream().filter(obj -> obj.get().isMarked()).forEach(obj -> obj.get().setMarked(false));

                PauseTransition pause3 = Helper.pause(Constants.PAUSE_TIME);
                pause3.setOnFinished(e3 -> {
                    // Resume all allocators
                    System.out.println("Resume allocators");
                    allocators.forEach(allctr -> allocator.resume());
                    this.collecting.set(false);
                    this.state.set(GCState.ALLOCATING);

                    // Check whether the memory can now be allocated and if not -> Out of memory exception
                    if (!allocator.allocate(heap, amountToAllocate)) {
                        System.out.println("Out of memory (not able to allocate " + amountToAllocate + " cells)");
                        this.state.set(GCState.OUT_OF_MEMORY);
                        mutatorThread.stop();
                    }
                });
                pause3.play();
            });
            pause2.play();
        });
        pause1.play();
    }

    public GCState getState() { return this.state.get(); }
    public void setState(final GCState state) { this.state.set(state); }
    public ObjectProperty<GCState> stateProperty() { return this.state; }

    public boolean isCollecting() { return this.collecting.get(); }
    public ReadOnlyBooleanProperty collectingProperty() { return this.collecting; }
}
