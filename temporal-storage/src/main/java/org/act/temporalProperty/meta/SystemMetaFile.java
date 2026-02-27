package org.act.temporalProperty.meta;

import org.act.temporalProperty.util.Slice;

import static org.act.temporalProperty.TemporalPropertyStore.MagicNumber;
import static org.act.temporalProperty.TemporalPropertyStore.Version;

/**
 * Created by song on 2018-01-18.
 */
public class SystemMetaFile{
    private final Slice meta;
    private final String magic;
    private final int version;
    private final long time;

    public SystemMetaFile(String magicNum, int version, long time, Slice meta) {
        this.magic = magicNum;
        this.version = version;
        this.time = time;
        this.meta = meta;
    }

    public Slice getMeta() {
        return meta;
    }

    public String getMagic() {
        return magic;
    }

    public int getVersion() {
        return version;
    }

    public long getTime() {
        return time;
    }

    public boolean isValid(){
        return this.magic.equals(MagicNumber) && this.version<=Version;
    }
}
