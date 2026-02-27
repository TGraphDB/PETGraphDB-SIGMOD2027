package org.act.temporalProperty.util;

import org.act.temporalProperty.index.aggregation.AggregationIndexMeta;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;

import java.util.Calendar;

/**
 * Created by song on 2018-05-09.
 */
public class ApacheDateUtilsTest
{
    @Test
    public void testCeiling(){
        Calendar t2010_11_04_14_00_00 = Calendar.getInstance();
        t2010_11_04_14_00_00.setTimeInMillis( 1288850400_000L );
        Calendar result = DateUtils.ceiling( t2010_11_04_14_00_00, Calendar.HOUR );
        System.out.println(result);
    }

}
