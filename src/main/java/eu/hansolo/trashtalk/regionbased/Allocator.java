package eu.hansolo.trashtalk.regionbased;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.trashtalk.CellState;
import eu.hansolo.trashtalk.regionbased.Constants.ObjRegionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static eu.hansolo.trashtalk.markandsweep.Constants.LIFE_TIMES_IN_MS;
import static eu.hansolo.trashtalk.markandsweep.Constants.RND;


public class Allocator {
    private boolean allocating;
    private boolean stopped;


    public Allocator() {
        this.allocating = false;
        this.stopped    = false;
    }


    public boolean allocate(final ObservableMatrix<ObjRegion> heap, final int amount, final List<ObjRegion> freeRegions, final List<ObjRegion> usedEdenRegions, final List<ObjRegion> usedSurvivorRegions, final List<ObjRegion> usedTenuredRegions, final List<ObjRegion> usedHumongousRegions) {
        boolean result = false;
        if (this.stopped) { return result; }
        this.allocating = true;
        record AllocatableObj(int x, int y, Obj block){};

        ObjRegion edenRegion;
        if (usedEdenRegions.size() == 0) {
            Collections.shuffle(freeRegions);
            edenRegion = freeRegions.stream().findFirst().get();
            freeRegions.remove(edenRegion);
            edenRegion.setType(ObjRegionType.EDEN);
            usedEdenRegions.add(edenRegion);
        } else {
            // Get Eden Region with free space or create a new one
            Optional<ObjRegion> optEdenRegion = usedEdenRegions.stream().filter(objRegion -> objRegion.isFreeSpaceAvailable()).findFirst();
            if (optEdenRegion.isPresent()) {
                edenRegion = optEdenRegion.get();
            } else {
                if (usedEdenRegions.size() == Constants.MAX_NO_OF_EDEN_REGIONS) {
                    // Garbage Collection
                    this.allocating = false;
                    return false;
                } else {
                    Collections.shuffle(freeRegions);
                    edenRegion = freeRegions.stream().findFirst().get();
                    freeRegions.remove(edenRegion);
                    edenRegion.setType(ObjRegionType.EDEN);
                    usedEdenRegions.add(edenRegion);
                }
            }
        }

        List<AllocatableObj> allocatableObjects = new ArrayList<>();
        int                  objCounter         = 0;
        for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
            for (int rx = 0 ; rx < Constants.REGION_ROWS ; rx++) {
                Obj obj = edenRegion.getObjMatrix().getItemAt(rx, ry);
                if (objCounter < amount && CellState.FREE == obj.getState()) {
                    objCounter++;
                    allocatableObjects.add(new AllocatableObj(rx, ry, obj));
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
            edenRegion.getObjMatrix().getItemAt(allocatableObj.x(), allocatableObj.y()).setState(CellState.REFERENCED);
            edenRegion.getObjMatrix().getItemAt(allocatableObj.x(), allocatableObj.y()).setLifeSpanInMilliSeconds(Constants.LIFE_TIMES_IN_MS[RND.nextInt(0, LIFE_TIMES_IN_MS.length)]);
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
