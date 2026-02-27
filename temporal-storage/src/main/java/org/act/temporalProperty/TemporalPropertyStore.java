package org.act.temporalProperty;

import org.act.temporalProperty.impl.MemTable;
import org.act.temporalProperty.index.IndexType;
import org.act.temporalProperty.index.value.IndexMetaData;
import org.act.temporalProperty.index.value.IndexQueryRegion;
import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationIndexQueryResult;
import org.act.temporalProperty.query.aggr.ValueGroupingMap;
import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
import org.act.temporalProperty.util.Slice;

import java.util.List;

/**
 * 时态属性存储系统，对外提供其功能的接口
 *
 */
public interface TemporalPropertyStore
{
	/**
	 * Get this by executing:
	 * echo https://github.com/TGraphDB/ | sha1sum
	 */
	String MagicNumber = "c003bf3c9563aa283d49c17fc13f736e5493107c"; //40bytes==160bits

	int Version = 2;

	/**
	 * 对某个时态属性进行时间点查询，返回查询的 结果
	 * @param entityId 时态属性所属的点/边的id
	 * @param proId 时态属性id
	 * @param time 需要查询的时间
	 * @return @{Slice} 查询的结果
	 */
    Slice getPointValue( long entityId, int proId, TimePointL time );
    
    /**
	 * 对某个时态属性进行时间段查询，返回查询的 结果
	 * @param id 时态属性所属的点/边的id
	 * @param proId 时态属性id
	 * @param startTime 需要查询的时间的起始时间
	 * @param endTime 需要查询的时间的结束时间
	 * @param callback 时间段查询所采用的聚集类型
	 * @return 用户在callback的onReturn函数中返回的结果，若callback为Aggregation的MIN或MAX函数，则结果集, get(MinMax.MIN)得到最小值, get(MinMax.MAX)得最大值. 若index中只定义了MIN,查询MAX为null
	 */
    Object getRangeValue(long id, int proId, TimePointL startTime, TimePointL endTime, InternalEntryRangeQueryCallBack callback );

    // query together with cache data
	Object getRangeValue(long entityId, int proId, TimePointL start, TimePointL end, InternalEntryRangeQueryCallBack callBack, MemTable cache );

	ValueContentType getPropertyValueType( int propertyId );

	/**
	 * 创建某个时态属性
	 * @param propertyId 时态属性的id
	 * @return 是否创建成功，如果有相同ID但类型不同的属性则返回false
	 */
	boolean createProperty(int propertyId, ValueContentType type);

    /**
     * 写入某个时态属性的值，值的起始时间和结束时间都是inclusive
     * @param key 由InternalKey(时态属性所属的点/边的id+时态属性id+相应值有效的起始时间)+endTime组成
     * @param value 值
     * @return 是否写入成功
     */
    boolean setProperty(TimeIntervalKey key, Slice value );
    
    /**
     * 删除某个时态属性
     * @param propertyId 时态属性的id
     * @return 是否删除成功
     */
    boolean deleteProperty(int propertyId);

	/**
	 * 删除某个时态属性中某个eid的所有数据
	 * @param id 时态属性的id + entity id
	 * @return 是否删除成功
	 */
	boolean deleteEntityProperty(Slice id);

	boolean deleteIndex(long indexId);

	/**
	 * Aggregation查询是getRangeValue的一种alias而已.
	 */
	Object aggregate(long entityId, int proId, TimePointL startTime, TimePointL endTime, InternalEntryRangeQueryCallBack callback);

	/**
	 * 创建Aggregation索引(可加速[在某段时间上对Value分组后统计各组时长]的查询操作).
	 * see {@link org.act.temporalProperty.index.aggregation.AggregationIndexMeta#calcInterval(int, int, int, int)} for more detail.
	 * 这个调用等索引建立完成才返回.
	 * @param propertyId    要索引的属性ID
	 * @param start         索引起始时间
	 * @param end           索引结束时间
	 * @param valueGrouping grouping values
	 * @param every         see {@link org.act.temporalProperty.index.aggregation.AggregationIndexMeta#calcInterval(int, int, int, int)} for more detail.
	 * @param timeUnit      can be Calendar.SECOND|HOUR|DAY|WEEK|SEMI_MONTH|MONTH|YEAR, see {@link org.act.temporalProperty.index.aggregation.AggregationIndexMeta#calcInterval(int, int, int, int)} for more detail.
	 * @return index ID
	 */
    long createAggrDurationIndex(int propertyId, TimePointL start, TimePointL end, ValueGroupingMap valueGrouping, int every, int timeUnit);

	/**
	 * 创建Aggregation索引(可加速[在某段时间上查找Value最大或最小值]的查询操作).
	 * @param propertyId 要索引的属性ID
	 * @param start      索引起始时间
	 * @param end        索引结束时间
	 * @param every      see {@link org.act.temporalProperty.index.aggregation.AggregationIndexMeta#calcInterval(int, int, int, int)} for more detail.
	 * @param timeUnit   can be Calendar.SECOND|HOUR|DAY|WEEK|SEMI_MONTH|MONTH|YEAR, see {@link org.act.temporalProperty.index.aggregation.AggregationIndexMeta#calcInterval(int, int, int, int)} for more detail.
	 * @param type       索引类型: 只索引最大值; 只索引最小值; 同时索引最大及最小值.
	 * @return index ID
	 */
	long createAggrMinMaxIndex(int propertyId, TimePointL start, TimePointL end, int every, int timeUnit, IndexType type);


	/**
	 * 使用Aggregation索引进行查询(加速)
	 * 注意这里不再需要指定是最大|最小|分组统计时长的查询, 因为查询类型已经包含在索引中了.
	 * 一旦索引的时间无法完全覆盖[startTime, endTime], 则会使用和创建索引相同的配置(如valueGroup)进行range查询
	 * @param indexId   要使用的索引的ID
	 * @param entityId  查询的entityID
	 * @param proId     要查询的属性id
	 * @param startTime 开始时间
	 * @param endTime   结束时间
	 * @return CallBack定义的返回
	 */
	AggregationIndexQueryResult getByIndex(long indexId, long entityId, int proId, TimePointL startTime, TimePointL endTime );

	// query together with cache data
	AggregationIndexQueryResult getByIndex(long indexId, long entityId, int proId, TimePointL startTime, TimePointL endTime, MemTable cache );
	/**
	 * 创建一个值索引
	 * @param start  索引开始时间
	 * @param end    索引结束时间
	 * @param proIds 索引的属性id列表
	 */
	long createValueIndex(TimePointL start, TimePointL end, List<Integer> proIds);

	/**
	 * get entity id which satisfy query condition
	 * @param condition query condition of one property
	 * @return null if no index available;
	 */
	List<Long> getEntities(IndexQueryRegion condition, MemTable cache);

	List<Long> getEntities(IndexQueryRegion condition);

	/**
	 * get index entries which satisfy query condition
	 * @param condition query condition of one property
	 * @return null if no index available;
	 */
	List<IndexEntry> getEntries(IndexQueryRegion condition, MemTable cache);

	long getCardinality( IndexQueryRegion condition, MemTable cache );

	List<IndexEntry> getEntries(IndexQueryRegion condition);

	List<IndexMetaData> listIndex();

	void flushMemTable2Disk();

    void flushMetaInfo2Disk();

    void shutDown() throws Exception;

}
