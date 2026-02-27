package org.act.temporalProperty.vo;

import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.io.Serializable;
import java.util.Objects;


public class EntityPropertyId implements Comparable<EntityPropertyId>, Serializable {
    private long entityId;
    private int propertyId;

    public EntityPropertyId(long entityId, int propertyId) {
        this.entityId = entityId;
        this.propertyId = propertyId;
    }

    public long getEntityId() {
        return entityId;
    }

    public int getPropertyId() {
        return propertyId;
    }

    @Override
    public int compareTo(EntityPropertyId o) {
        int result = Integer.compare(propertyId, o.propertyId);
        if(result==0){
            return Long.compare(entityId, o.entityId);
        }else{
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityPropertyId that = (EntityPropertyId) o;
        return entityId == that.entityId &&
                propertyId == that.propertyId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, propertyId);
    }

    @Override
    public String toString() {
        return "EntityPropertyId{" +
                "entityId=" + entityId +
                ", propertyId=" + propertyId +
                '}';
    }

    public void encode(SliceOutput out){
        out.writeInt(propertyId);
        out.writeLong(entityId);
    }

    public static EntityPropertyId decode(SliceInput in){
        int propId = in.readInt();
        long entityId = in.readLong();
        return new EntityPropertyId(entityId, propId);
    }

    public int byteCount() {
        return 16;
    }
}
