//package org.act.temporalProperty.impl;
//
//import org.act.temporalProperty.util.TemporalPropertyValueConvertor;
//import org.act.temporalProperty.util.Slice;
//
//public class RangeQueryUtil {
//
//	public static Slice max(Slice max, Slice value) {
//		if( max == null )
//			return value;
//		if( value == null )
//			return max;
//		if(max.length() != value.length() )
//			return max;
//		byte[] maxByte = max.copyBytes();
//		byte[] valueByte = value.copyBytes();
//		if(maxByte.length == 1){
//			byte maxV = (byte) TemporalPropertyValueConvertor.revers("Byte", maxByte);
//			byte minV = (byte) TemporalPropertyValueConvertor.revers("Byte", valueByte);
//			if( maxV > minV )
//				return max;
//			else
//				return value;
//		} else if(max.length() == 4){
//			int maxV = (int) TemporalPropertyValueConvertor.revers("Integer", maxByte);
//			int minV = (int) TemporalPropertyValueConvertor.revers("Integer", valueByte);
//			if( maxV > minV )
//				return max;
//			else
//				return value;
//		} else if( max.length() == 8 ){
//			long maxV = (long) TemporalPropertyValueConvertor.revers("Long", maxByte);
//			long minV = (long) TemporalPropertyValueConvertor.revers("Long", valueByte);
//			if( maxV > minV )
//				return max;
//			else
//				return value;
//		}
//		return max;
//	}
//
//	public static Slice min(Slice max, Slice value) {
//		if( max == null )
//			return value;
//		if( value == null )
//			return max;
//		if(max.length() != value.length() )
//			return max;
//		byte[] maxByte = max.copyBytes();
//		byte[] valueByte = value.copyBytes();
//		if(maxByte.length == 1){
//			byte maxV = (byte) TemporalPropertyValueConvertor.revers("Byte", maxByte);
//			byte minV = (byte) TemporalPropertyValueConvertor.revers("Byte", valueByte);
//			if( maxV < minV )
//				return max;
//			else
//				return value;
//		} else if(max.length() == 4){
//			int maxV = (int) TemporalPropertyValueConvertor.revers("Integer", maxByte);
//			int minV = (int) TemporalPropertyValueConvertor.revers("Integer", valueByte);
//			if( maxV < minV )
//				return max;
//			else
//				return value;
//		} else if( max.length() == 8 ){
//			long maxV = (long) TemporalPropertyValueConvertor.revers("Long", maxByte);
//			long minV = (long) TemporalPropertyValueConvertor.revers("Long", valueByte);
//			if( maxV < minV )
//				return max;
//			else
//				return value;
//		}
//		return max;
//	}
//
//	public static Slice sum(Slice max, Slice value) {
//		if( max == null )
//			return value;
//		if( value == null )
//			return max;
//		if(max.length() != value.length() )
//			return max;
//		byte[] maxByte = max.copyBytes();
//		byte[] valueByte = value.copyBytes();
//		if(maxByte.length == 1){
//			byte maxV = (byte) TemporalPropertyValueConvertor.revers("Byte", maxByte);
//			byte minV = (byte) TemporalPropertyValueConvertor.revers("Byte", valueByte);
//			byte toret = (byte)(maxV+minV);
//			Slice tSlice = new Slice(0);
//			tSlice.setByte(0, toret);
//			return tSlice;
//
//		} else if(max.length() == 4){
//			int maxV = (int) TemporalPropertyValueConvertor.revers("Integer", maxByte);
//			int minV = (int) TemporalPropertyValueConvertor.revers("Integer", valueByte);
//			int toret = maxV+minV;
//			Slice tSlice = new Slice(4);
//			tSlice.setInt(0, toret);
//			return tSlice;
//		} else if( max.length() == 8 ){
//			long maxV = (long) TemporalPropertyValueConvertor.revers("Long", maxByte);
//			long minV = (long) TemporalPropertyValueConvertor.revers("Long", valueByte);
//			long toret = (long)(maxV+minV);
//			Slice tSlice = new Slice(0);
//			tSlice.setLong(0, toret);
//			return tSlice;
//		}
//		return max;
//	}
//
//}
