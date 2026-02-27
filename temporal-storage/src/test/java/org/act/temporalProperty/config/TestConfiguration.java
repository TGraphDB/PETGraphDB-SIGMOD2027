package org.act.temporalProperty.config;

import org.apache.commons.lang3.SystemUtils;


/**
 * The fields in this classes is common when test.
 * <p>
 * You can add your config too.
 * - sjh-dev-windows
 * - sjh-dev-linux,
 * - travis-ci-linux
 * - yangfan-windows
 */
public abstract class TestConfiguration {
    private static TestConfiguration config = null;

    public static TestConfiguration get() {
        if( config!=null ) return config;

        if (SystemUtils.IS_OS_WINDOWS) {
            if ("Song".equals(System.getProperty("user.name"))) {
                config = new SJH_WIN();
            } else {
                config = new YangFan_Dev();
            }
        } else {
            if ("yes".equals(System.getenv("IS_CI_BUILDING"))) {
                config = new Travis_CI();
            } else {
                config = new SJH_Linux();
            }
        }
        return config;
    }

    /**
     * Methods which a config must override.
     *
     */
    abstract public String testDataDir();

    abstract public String dbDir();


    /**
     * Song Jing He 's Win10 Desktop System
     */
    private static class SJH_WIN extends TestConfiguration {
        public String testDataDir() {
            return "test-data";
        }

        public String dbDir() {
            return "temporal.property.test";
        }
    }


    private static class SJH_Linux extends TestConfiguration {
        public String testDataDir() {
            return "test-data";
        }

        public String dbDir() {
            return "/tmp/temporal.property.test";
        }
    }

    /**
     * Test running on travis-ci.org
     */
    private static class Travis_CI extends TestConfiguration {
        public String testDataDir() {
            return System.getenv("HOME") + "/test-data";
        }

        public String dbDir() {
            return "temporal.property.test";
        }
    }

    private static class YangFan_Dev extends TestConfiguration {
        @Override
        public String testDataDir() {
            return "C:\\Users\\Administrator\\Desktop\\TGraph-source\\20101104.tar\\20101104";
        }

        @Override
        public String dbDir() {
            return "temporal.property.test";
        }
    }
}
