package eu.hansolo.trashtalk.regionbased;

import eu.hansolo.toolbox.observables.ObservableMatrix;
import eu.hansolo.trashtalk.CellState;
import eu.hansolo.trashtalk.GCState;
import eu.hansolo.trashtalk.Helper;
import eu.hansolo.trashtalk.regionbased.Constants.ObjRegionType;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


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


    public void collect(final List<Allocator> allocators, final ObservableMatrix<ObjRegion> heap, final Allocator allocator, final int amountToAllocate, AnimationTimer mutatorThread, final List<ObjRegion> freeRegions, final List<ObjRegion> usedEdenRegions, final List<ObjRegion> usedSurvivorRegions, final List<ObjRegion> usedTenuredRegions, final List<ObjRegion> usedHumongousRegions) {
        this.collecting.set(true);
        this.state.set(GCState.COPYING);

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
                ObjRegion objRegion = heap.getItemAt(x, y);
                for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
                    for (int rx = 0; rx < Constants.REGION_COLS ; rx++) {
                        Obj obj = objRegion.getObjMatrix().getItemAt(rx, ry);
                        if (CellState.REFERENCED == obj.getState()) {
                            obj.incSurvivedGarbageCollections();
                        }
                    }
                }
            }
        }

        System.out.println("usedEdenRegions    : " + usedEdenRegions.size());
        System.out.println("usedSurvivorRegions: " + usedSurvivorRegions.size());
        System.out.println("usedTenuredRegions : " + usedTenuredRegions.size());

        // Get next Survivor Region with free cells or create new one
        ObjRegion survivorRegion = null;
        if (usedSurvivorRegions.size() == 0) {
            Collections.shuffle(freeRegions);
            survivorRegion = freeRegions.stream().findFirst().get();
            freeRegions.remove(survivorRegion);
            survivorRegion.setType(ObjRegionType.SURVIVOR);
            usedSurvivorRegions.add(survivorRegion);
        } else {
            // Get Survivor Region with free space or create a new one
            Optional<ObjRegion> optSurvivorRegion = usedSurvivorRegions.stream().filter(objRegion -> objRegion.isFreeSpaceAvailable()).findFirst();
            if (optSurvivorRegion.isPresent()) {
                survivorRegion = optSurvivorRegion.get();
            } else {
                if (usedSurvivorRegions.size() == Constants.MAX_NO_OF_SURVIVOR_REGIONS) {
                    // Promote live objects from survivor regions to tenured regions
                    System.out.println("Promote survived objects to tenured space (1)");
                    // Get Tenured Region with free space or create a new one
                    ObjRegion tenuredRegion = null;
                    if (usedTenuredRegions.size() == 0) {
                        Collections.shuffle(freeRegions);
                        tenuredRegion = freeRegions.stream().findFirst().get();
                        freeRegions.remove(tenuredRegion);
                        tenuredRegion.setType(ObjRegionType.TENURED);
                        usedTenuredRegions.add(tenuredRegion);
                    } else {
                        // Get Tenured Region with free space or create a new one
                        Optional<ObjRegion> optTenuredRegion = usedTenuredRegions.stream().filter(objRegion -> objRegion.isFreeSpaceAvailable()).findFirst();
                        if (optTenuredRegion.isPresent()) {
                            tenuredRegion = optTenuredRegion.get();
                        } else {
                            // If no free memory available -> throw out of memory exception
                            Collections.shuffle(freeRegions);
                            tenuredRegion = freeRegions.stream().findFirst().get();
                            freeRegions.remove(tenuredRegion);
                            tenuredRegion.setType(ObjRegionType.TENURED);
                            usedTenuredRegions.add(tenuredRegion);
                        }
                    }
                    // Promote live cells from first 2 survivor regions with most garbage to tenured region
                    List<ObjRegion> survivorRegionsWithMostGarbage = usedSurvivorRegions.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).sorted(Comparator.comparingLong(ObjRegion::getAmountOfGarbage).reversed()).limit(2).toList();
                    List<Obj>       liveSurvivorCells              = new ArrayList<>();
                    for (int i = 0 ; i < survivorRegionsWithMostGarbage.size() ; i++) {
                        ObjRegion objRegion = survivorRegionsWithMostGarbage.get(i);
                        liveSurvivorCells.addAll(objRegion.getObjMatrix().stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).map(ref -> ref.get()).toList());
                    }

                    System.out.println(liveSurvivorCells.size() + " live cells found in survivor regions to be copied to tenured region");
                    for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
                        for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                            if (liveSurvivorCells.size() > 0) {
                                Obj cell = tenuredRegion.getObjMatrix().getItemAt(rx, ry);
                                if (CellState.FREE == cell.getState()) {
                                    cell.setState(CellState.REFERENCED);
                                    cell.setLifeSpanInMilliSeconds(liveSurvivorCells.get(0).getLifeSpanInMilliSeconds());
                                    Optional<ObjRegion> optObjRegion = survivorRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                                    if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                                    liveSurvivorCells.remove(0);
                                }
                            } else {
                                break;
                            }
                        }
                    }

                    // Not enough space in current tenured region, create a new one and continue copying live cells from survivor regions
                    if (!liveSurvivorCells.isEmpty()) {
                        // Find Tenured regions with complete garbage and free them (set them to free regions)
                        List<ObjRegion> fullGarbageTenuredRegions = usedTenuredRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
                        for (ObjRegion objRegion : fullGarbageTenuredRegions) {
                            usedTenuredRegions.remove(objRegion);
                            objRegion.freeRegion();
                            freeRegions.add(objRegion);
                        }
                        Collections.shuffle(freeRegions);
                        tenuredRegion = freeRegions.stream().findFirst().get();
                        freeRegions.remove(tenuredRegion);
                        tenuredRegion.setType(ObjRegionType.TENURED);
                        usedTenuredRegions.add(tenuredRegion);

                        for (int ry = 0; ry < Constants.REGION_ROWS; ry++) {
                            for (int rx = 0; rx < Constants.REGION_COLS; rx++) {
                                if (liveSurvivorCells.size() > 0) {
                                    Obj cell = tenuredRegion.getObjMatrix().getItemAt(rx, ry);
                                    cell.setState(CellState.REFERENCED);
                                    cell.setLifeSpanInMilliSeconds(liveSurvivorCells.get(0).getLifeSpanInMilliSeconds());
                                    Optional<ObjRegion> optObjRegion = survivorRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                                    if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                                    liveSurvivorCells.remove(0);
                                } else {
                                    break;
                                }
                            }
                        }
                    }

                    List<ObjRegion> fullGarbageSurvivorRegions = usedSurvivorRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
                    for (ObjRegion objRegion : fullGarbageSurvivorRegions) {
                        usedSurvivorRegions.remove(objRegion);
                        objRegion.freeRegion();
                        freeRegions.add(objRegion);
                    }

                    // TEST
                    Collections.shuffle(freeRegions);
                    survivorRegion = freeRegions.stream().findFirst().get();
                    freeRegions.remove(survivorRegion);
                    survivorRegion.setType(ObjRegionType.SURVIVOR);
                    usedSurvivorRegions.add(survivorRegion);
                } else {
                    Collections.shuffle(freeRegions);
                    survivorRegion = freeRegions.stream().findFirst().get();
                    freeRegions.remove(survivorRegion);
                    survivorRegion.setType(ObjRegionType.SURVIVOR);
                    usedSurvivorRegions.add(survivorRegion);
                }
            }
        }

        // Find first 2 Eden regions with most garbage and copy live objects to a survivor region
        List<ObjRegion> edenRegionsWithMostGarbage = usedEdenRegions.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).sorted(Comparator.comparingLong(ObjRegion::getAmountOfGarbage).reversed()).limit(2).toList();
        List<Obj>       liveEdenCells              = new ArrayList<>();
        for (int i = 0 ; i < edenRegionsWithMostGarbage.size() ; i++) {
            ObjRegion objRegion = edenRegionsWithMostGarbage.get(i);
            liveEdenCells.addAll(objRegion.getObjMatrix().stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).map(ref -> ref.get()).toList());
        }

        System.out.println(liveEdenCells.size() + " live cells found in eden regions to be copied to survivor region");
        for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
            for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                if (liveEdenCells.size() > 0) {
                    Obj cell = survivorRegion.getObjMatrix().getItemAt(rx, ry);
                    if (CellState.FREE == cell.getState()) {
                        cell.setState(CellState.REFERENCED);
                        cell.setLifeSpanInMilliSeconds(liveEdenCells.get(0).getLifeSpanInMilliSeconds());
                        Optional<ObjRegion> optObjRegion = edenRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                        if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                        liveEdenCells.remove(0);
                    }
                } else {
                    break;
                }
            }
        }

        // Not enough space in current survivor region, create a new one and continue copying live cells
        if (!liveEdenCells.isEmpty()) {
            // Find Survivor regions with complete garbage and free them (set them to free regions)
            List<ObjRegion> fullGarbageSurvivorRegions = usedSurvivorRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
            for (ObjRegion objRegion : fullGarbageSurvivorRegions) {
                usedSurvivorRegions.remove(objRegion);
                objRegion.freeRegion();
                freeRegions.add(objRegion);
            }

            if (usedSurvivorRegions.size() < Constants.MAX_NO_OF_SURVIVOR_REGIONS) {
                Collections.shuffle(freeRegions);
                survivorRegion = freeRegions.stream().findFirst().get();
                survivorRegion.setType(ObjRegionType.SURVIVOR);
                freeRegions.remove(survivorRegion);
                usedSurvivorRegions.add(survivorRegion);

                for (int ry = 0; ry < Constants.REGION_ROWS; ry++) {
                    for (int rx = 0; rx < Constants.REGION_COLS; rx++) {
                        if (liveEdenCells.size() > 0) {
                            Obj cell = survivorRegion.getObjMatrix().getItemAt(rx, ry);
                            cell.setState(CellState.REFERENCED);
                            cell.setLifeSpanInMilliSeconds(liveEdenCells.get(0).getLifeSpanInMilliSeconds());
                            Optional<ObjRegion> optObjRegion = edenRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                            if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                            liveEdenCells.remove(0);
                        } else {
                            break;
                        }
                    }
                }
            } else {
                // Promote live objects from survivor regions to tenured regions
                System.out.println("Promote survived objects to tenured space (2)");
                // Get Tenured Region with free space or create a new one
                ObjRegion tenuredRegion = null;
                if (usedTenuredRegions.size() == 0) {
                    Collections.shuffle(freeRegions);
                    tenuredRegion = freeRegions.stream().findFirst().get();
                    freeRegions.remove(tenuredRegion);
                    tenuredRegion.setType(ObjRegionType.TENURED);
                    usedTenuredRegions.add(tenuredRegion);
                } else {
                    // Get Tenured Region with free space or create a new one
                    Optional<ObjRegion> optTenuredRegion = usedTenuredRegions.stream().filter(objRegion -> objRegion.isFreeSpaceAvailable()).findFirst();
                    if (optTenuredRegion.isPresent()) {
                        tenuredRegion = optTenuredRegion.get();
                    } else {
                        // If no free memory available -> throw out of memory exception
                        Collections.shuffle(freeRegions);
                        tenuredRegion = freeRegions.stream().findFirst().get();
                        freeRegions.remove(tenuredRegion);
                        tenuredRegion.setType(ObjRegionType.TENURED);
                        usedTenuredRegions.add(tenuredRegion);
                    }
                }
                // Promote live cells from survivor regions to tenured region
                List<ObjRegion> survivorRegionsWithMostGarbage = usedSurvivorRegions.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).sorted(Comparator.comparingLong(ObjRegion::getAmountOfGarbage).reversed()).limit(2).toList();
                List<Obj>       liveSurvivorCells              = new ArrayList<>();
                for (int i = 0 ; i < survivorRegionsWithMostGarbage.size() ; i++) {
                    ObjRegion objRegion = survivorRegionsWithMostGarbage.get(i);
                    liveSurvivorCells.addAll(objRegion.getObjMatrix().stream().filter(ref -> CellState.REFERENCED == ref.get().getState()).map(ref -> ref.get()).toList());
                }
                System.out.println(liveSurvivorCells.size() + " live cells found in survivor regions to be copied to tenured region");
                for (int ry = 0 ; ry < Constants.REGION_ROWS ; ry++) {
                    for (int rx = 0 ; rx < Constants.REGION_COLS ; rx++) {
                        if (liveSurvivorCells.size() > 0) {
                            Obj cell = tenuredRegion.getObjMatrix().getItemAt(rx, ry);
                            if (CellState.FREE == cell.getState()) {
                                cell.setState(CellState.REFERENCED);
                                cell.setLifeSpanInMilliSeconds(liveSurvivorCells.get(0).getLifeSpanInMilliSeconds());
                                Optional<ObjRegion> optObjRegion = survivorRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                                if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                                liveSurvivorCells.remove(0);
                            }
                        } else {
                            break;
                        }
                    }
                }

                // Not enough space in current tenured region, create a new one and continue copying live cells from survivor regions
                if (!liveSurvivorCells.isEmpty()) {
                    // Find Tenured regions with complete garbage and free them (set them to free regions)
                    List<ObjRegion> fullGarbageTenuredRegions = usedTenuredRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
                    for (ObjRegion objRegion : fullGarbageTenuredRegions) {
                        usedTenuredRegions.remove(objRegion);
                        objRegion.freeRegion();
                        freeRegions.add(objRegion);
                    }

                    Collections.shuffle(freeRegions);
                    tenuredRegion = freeRegions.stream().findFirst().get();
                    freeRegions.remove(tenuredRegion);
                    tenuredRegion.setType(ObjRegionType.TENURED);
                    usedTenuredRegions.add(tenuredRegion);

                    for (int ry = 0; ry < Constants.REGION_ROWS; ry++) {
                        for (int rx = 0; rx < Constants.REGION_COLS; rx++) {
                            if (liveSurvivorCells.size() > 0) {
                                Obj cell = tenuredRegion.getObjMatrix().getItemAt(rx, ry);
                                cell.setState(CellState.REFERENCED);
                                cell.setLifeSpanInMilliSeconds(liveSurvivorCells.get(0).getLifeSpanInMilliSeconds());
                                Optional<ObjRegion> optObjRegion = survivorRegionsWithMostGarbage.stream().filter(objRegion -> !objRegion.isCompletelyGarbage()).findFirst();
                                if (optObjRegion.isPresent()) { optObjRegion.get().copyNextLiveCell(); }
                                liveSurvivorCells.remove(0);
                            } else {
                                break;
                            }
                        }
                    }
                }

                fullGarbageSurvivorRegions = usedSurvivorRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
                for (ObjRegion objRegion : fullGarbageSurvivorRegions) {
                    usedSurvivorRegions.remove(objRegion);
                    objRegion.freeRegion();
                    freeRegions.add(objRegion);
                }
            }
        }

        PauseTransition pause1 = Helper.pause(Constants.PAUSE_TIME);
        pause1.setOnFinished(e1 -> {
            // Free all dead objects
            System.out.println("Free dead objects");
            this.state.set(GCState.FREEING);

            // Find Eden regions with complete garbage and free them (set them to free regions)
            List<ObjRegion> fullGarbageEdenRegions = usedEdenRegions.stream().filter(objRegion -> objRegion.isCompletelyGarbage()).toList();
            for (ObjRegion objRegion : fullGarbageEdenRegions) {
                usedEdenRegions.remove(objRegion);
                objRegion.freeRegion();
                freeRegions.add(objRegion);
            }

            edenRegionsWithMostGarbage.forEach(objRegion -> {
                usedEdenRegions.remove(objRegion);
                objRegion.freeRegion();
                freeRegions.add(objRegion);
            });

            PauseTransition pause2 = Helper.pause(Constants.PAUSE_TIME);
            pause2.setOnFinished(e2 -> {
                PauseTransition pause3 = Helper.pause(Constants.PAUSE_TIME);
                pause3.setOnFinished(e3 -> {
                    // Resume all allocators
                    System.out.println("Resume allocators");
                    allocators.forEach(allctr -> allocator.resume());
                    this.collecting.set(false);
                    this.state.set(GCState.ALLOCATING);

                    // Check whether the memory can now be allocated and if not -> Out of memory exception
                    if (!allocator.allocate(heap, amountToAllocate, freeRegions, usedEdenRegions, usedSurvivorRegions, usedTenuredRegions, usedHumongousRegions)) {
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
