package org.act.temporalProperty.impl;

import java.io.IOException;

/**
 * Created by song on 2018-05-07.
 */
public interface BackgroundTask
{
    void runTask() throws IOException;

    void updateMeta() throws IOException;

    void cleanUp() throws IOException;
}
