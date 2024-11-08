package eu.hansolo.trashtalk.markandcompact;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.trashtalk.CellState;

import java.util.ArrayList;
import java.util.List;

import static eu.hansolo.trashtalk.markandcompact.Constants.LIFE_TIMES_IN_MS;
import static eu.hansolo.trashtalk.markandcompact.Constants.RND;


public class Allocator {
    private boolean allocating;
    private boolean stopped;


    public Allocator() {
        this.allocating = false;
        this.stopped    = false;
    }


    public boolean allocate(final ObservableMatrix<Obj> heap, final int amount) {
        boolean result = false;
        if (this.stopped) { return result; }
        this.allocating = true;
        record AllocatableObj(int x, int y, Obj block){};
        List<AllocatableObj> allocatableObjects = new ArrayList<>();
        int                  objCounter         = 0;
        for (int y = 0 ; y < heap.getNoOfRows() ; y++) {
            for (int x = 0 ; x < heap.getNoOfCols() ; x++) {
                Obj obj = heap.getItemAt(x, y);
                if (objCounter < amount && CellState.FREE == obj.getState() && y != heap.getNoOfCols() + 1) {
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
            heap.getItemAt(allocatableObj.x(), allocatableObj.y()).setState(CellState.REFERENCED);
            heap.getItemAt(allocatableObj.x(), allocatableObj.y()).setLifeSpanInMilliSeconds(Constants.LIFE_TIMES_IN_MS[RND.nextInt(0, LIFE_TIMES_IN_MS.length)]);
        });
        this.allocating = false;
        return result;
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
