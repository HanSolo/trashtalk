package eu.hansolo.trashtalk.regionbased;

import eu.hansolo.trashtalk.CellState;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.LongProperty;
import javafx.beans.property.LongPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;


public class Obj {
    private ObjectProperty<CellState> state;
    private IntegerProperty           survivedGarbageCollections;
    private LongProperty              lifeSpanInMilliSeconds;



    public Obj() {
        this.state                      = new ObjectPropertyBase<>(CellState.FREE) {
            @Override protected void invalidated() {
                switch(get()) {
                    case FREE         -> {
                        survivedGarbageCollections.set(0);
                    }
                    case DEREFERENCED -> survivedGarbageCollections.set(0);
                }
            }
            @Override public Object getBean() { return Obj.this; }
            @Override public String getName() { return "state"; }
        };
        this.survivedGarbageCollections = new IntegerPropertyBase(0) {
            @Override public Object getBean() { return Obj.this; }
            @Override public String getName() { return "survivedGarbageCollections"; }
        };
        this.lifeSpanInMilliSeconds     = new LongPropertyBase(0) {
            @Override public Object getBean() { return Obj.this; }
            @Override public String getName() { return "lifeSpanInMillisSeconds"; }
        };
    }


    public CellState getState() { return this.state.get(); }
    public void setState(final CellState cellState)  { this. state.set(cellState); }
    public ObjectProperty<CellState> stateProperty() { return this.state; }

    public void update(final Obj sourceObj) {
        // Header Bits
        this.state.set(sourceObj.getState());
        this.survivedGarbageCollections.set(sourceObj.getSurvivedGarbageCollections());
        this.lifeSpanInMilliSeconds.set(sourceObj.getLifeSpanInMilliSeconds());
    }

    public int getSurvivedGarbageCollections() { return this.survivedGarbageCollections.get(); }
    public void setSurvivedGarbageCollections(final int survivedGarbageCollections) { this.survivedGarbageCollections.set(survivedGarbageCollections); }
    public IntegerProperty survivedGarbageCollectionsProperty() { return this.survivedGarbageCollections; }
    /**
     * Increase survivedGarbageCollection counter
     */
    public void incSurvivedGarbageCollections() {
        this.survivedGarbageCollections.set(this.survivedGarbageCollections.get() + 1);
    }


    public long getLifeSpanInMilliSeconds() { return this.lifeSpanInMilliSeconds.get(); }
    public void setLifeSpanInMilliSeconds(final long lifeSpanInMilliSeconds) {
        this.lifeSpanInMilliSeconds.set(lifeSpanInMilliSeconds);
    }
    public LongProperty lifeSpanInMilliSecondsProperty() { return this.lifeSpanInMilliSeconds; }
    /**
     * Decreases the lifespan of the object and once it reaches 0,
     * the state will be set to DEREFERENCED so that it will be
     * garbage collected with the next run
     */
    public void decLifespan() {
        if (this.state.get() == CellState.REFERENCED) {
            this.lifeSpanInMilliSeconds.set(this.lifeSpanInMilliSeconds.get() - Constants.AGING_AMOUNT);
            if (lifeSpanInMilliSeconds.get() < 0) {
                this.state.set(CellState.DEREFERENCED);
            }
        }
    }
}
