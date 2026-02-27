package org.act.temporalProperty.meta;

/**
 * Created by song on 2018-01-17.
 */
public enum ValueContentType {
    INT(1), LONG(2), FLOAT(3), DOUBLE(4), SLICE(5);
    private final int id;
    ValueContentType(int id){this.id=id;}
    public int getId(){return id;}

    public static ValueContentType decode(int i) {
        switch (i){
            case 1: return INT;
            case 2: return LONG;
            case 3: return FLOAT;
            case 4: return DOUBLE;
            default: return SLICE;
        }
    }
}
