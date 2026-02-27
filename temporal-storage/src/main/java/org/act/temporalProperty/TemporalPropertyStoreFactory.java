package org.act.temporalProperty;

import org.act.temporalProperty.impl.TemporalPropertyStoreImpl;

import java.io.File;

/**
 * 新建动态属性存储的工厂类
 *
 */
public class TemporalPropertyStoreFactory
{
	/**
	 * 返回新的动态属性存储实例
	 * @param dbDir 保存动态属性存储文件的目录
	 * @return
	 */
    public static TemporalPropertyStore newPropertyStore(File dbDir ) throws Exception {
        return new TemporalPropertyStoreImpl( dbDir, false );
    }

	public static TemporalPropertyStore bulkPropertyStore(File dbDir ) throws Exception {
		return new TemporalPropertyStoreImpl( dbDir, true );
	}
}
