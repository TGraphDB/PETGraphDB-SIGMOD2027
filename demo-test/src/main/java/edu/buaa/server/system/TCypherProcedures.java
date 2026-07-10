/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.buaa.server.system;

import org.act.temporalProperty.query.TimePointL;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.temporal.TemporalRangeQuery;
import org.neo4j.graphdb.temporal.TimeIntervalRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;
import org.neo4j.procedure.*;
import org.neo4j.logging.Log;

import java.io.IOException;
import java.util.Objects;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TCypherProcedures {
    @Context
    public Log log;

    @Procedure(name = "tp.set", mode = Mode.WRITE)
    @Description("Set temporal property. Signature: setTP(entity, property, timeBegin, timeEnd_optional, value).")
    public Stream<MutateResult> setTemporalProperty(
            @Name("entity") Object entity,
            @Name("property") String property,
            @Name("timeBegin") Object timeBegin,
            @Name(value = "timeEndOrValue", defaultValue = "__MISSING__") Object timeEndOrValue,
            @Name(value = "value", defaultValue = "__MISSING__") Object value)
            throws IOException {

        List<CallParam> calls = CallParam.parseSet(entity, property, timeBegin, timeEndOrValue, value);
        for (CallParam call : calls) {
            if (call.isRange()) {
                call.entity.setTemporalProperty(call.property, call.start, call.end, call.value);
            } else {
                call.entity.setTemporalProperty(call.property, call.start, call.value);
            }
        }

        return Stream.of(new MutateResult(calls.size()));
    }

    @Procedure(name = "tp.get", mode = Mode.READ)
    @Description("Get temporal property. Signature: getTP(entity, property, timeBegin, timeEnd_optional).")
    public Stream<TemporalTriple> getTemporalProperty(
            @Name("entity") Object entity,
            @Name("property") String property,
            @Name("timeBegin") Object timeBegin,
            @Name(value = "timeEnd", defaultValue = "__MISSING__") Object timeEnd)
            throws IOException {

        CallParam call = CallParam.parseGet(entity, property, timeBegin, timeEnd);
        if (call.isRange()) {
            List<TemporalTriple> triples = new ArrayList<>();
            call.entity.getTemporalProperty(call.property, call.start, call.end, new TimeIntervalRangeQuery(call.start, call.end) {
                @Override
                public void onEntry(TimePointL beginTime, TimePointL endTime, Object val) {
                    if (val != null) {
                        long beginTs = beginTime.getTime();
                        long endTs = endTime.isNow() ? Integer.MAX_VALUE : endTime.getTime();
                        triples.add(new TemporalTriple(beginTs, endTs, val));
                    }
                }
            });
            return triples.stream();
        } else {
            Object result = call.entity.getTemporalProperty(call.property, call.start);
            return Stream.of(new TemporalTriple(call.start.val(), call.start.val(), result));
        }
    }

    public static class CallParam {
        private static final String MISSING = "__MISSING__";

        public final Entity entity;
        public final String property;
        public final TimePoint start;
        public final TimePoint end;
        public final Object value;

        private CallParam(
                Entity entity,
                String property,
                TimePoint start,
                TimePoint end,
                Object value) {
            this.entity = entity;
            this.property = property;
            this.start = start;
            this.end = end;
            this.value = value;
        }

        static List<CallParam> parseSet(Object entity, String property, Object timeBegin, Object timeEndOrValue, Object value) {
            Entity targetEntity = requireEntity(entity);
            String targetProperty = requireProperty(property);

            Object actualValue;
            Object actualEnd = null;
            boolean rangeMode;

            if (isMissingArg(value)) {
                if (isMissingArg(timeEndOrValue)) {
                    throw new IllegalArgumentException("setTP(entity, property, timeBegin, value) requires value");
                }
                actualValue = timeEndOrValue;
                rangeMode = false;
            } else {
                actualEnd = timeEndOrValue;
                actualValue = value;
                rangeMode = true;
            }

            List<TimePoint> startList = parseTimePointListOrSingle(timeBegin);
            List<TimePoint> endList = rangeMode ? parseTimePointListOrSingle(actualEnd) : null;
            List<Object> valueList = parseValueListOrSingle(actualValue);

            if (startList.size() != valueList.size()) {
                throw new IllegalArgumentException("batch argument size mismatch: len(timeBegin)=" + startList.size() + " while len(value)=" + valueList.size());
            }else if(rangeMode && startList.size()!= endList.size()){
                throw new IllegalArgumentException("batch argument size mismatch: len(timeBegin)=" + startList.size() + " while len(timeEnd)=" + endList.size());
            }

            List<CallParam> calls = new ArrayList<>(startList.size());
            for (int i = 0; i < startList.size(); i++) {
                calls.add(new CallParam(targetEntity, targetProperty,
                        startList.get(i), rangeMode ? endList.get(i) : null, valueList.get(i)));
            }

            return calls;
        }

        static CallParam parseGet(Object entity, String property, Object timeBegin, Object timeEnd) {
            Entity targetEntity = requireEntity(entity);
            String targetProperty = requireProperty(property);

            TimePoint start = parseTimePoint(timeBegin);
            TimePoint end = isMissingArg(timeEnd) ? null : parseTimePoint(timeEnd);
            return new CallParam(targetEntity, targetProperty, start, end, null);
        }

        private static Entity requireEntity(Object entity) {
            if (!(entity instanceof Entity)) {
                throw new IllegalArgumentException("entity must be node or relationship");
            }
            return (Entity) entity;
        }

        private static String requireProperty(String property) {
            if (property == null || property.isBlank()) {
                throw new IllegalArgumentException("property cannot be blank");
            }
            return property.trim();
        }

        private static boolean isMissingArg(Object value) {
            return value instanceof String && MISSING.equals(value);
        }

        private static TimePoint toTimePoint(Number timestamp) {
            long ts = timestamp.longValue();
            if (ts < Integer.MIN_VALUE || ts > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("timestamp out of int range: " + ts);
            }
            return new TimePoint((int) ts);
        }

        private static TimePoint parseTimePoint(Object time) {
            Objects.requireNonNull(time, "time cannot be null");

            if (time instanceof Number) {
                return toTimePoint((Number) time);
            }

            if (time instanceof String) {
                String input = ((String) time).trim();
                if (input.isEmpty()) {
                    throw new IllegalArgumentException("time string cannot be blank");
                }

                if (input.matches("^-?\\d+$")) {
                    return toTimePoint(Long.parseLong(input));
                }

                try {
                    long epochSeconds = Instant.parse(input).getEpochSecond();
                    return toTimePoint(epochSeconds);
                } catch (DateTimeParseException ignore) {
                    // try local date/datetime patterns below
                }

                List<DateTimeFormatter> dateTimeFormats = Arrays.asList(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                );
                for (DateTimeFormatter formatter : dateTimeFormats) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(input, formatter);
                        long epochSeconds = dt.atZone(ZoneId.systemDefault()).toEpochSecond();
                        return toTimePoint(epochSeconds);
                    } catch (DateTimeParseException ignore) {
                        // try next format
                    }
                }

                List<DateTimeFormatter> dateFormats = Arrays.asList(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd")
                );
                for (DateTimeFormatter formatter : dateFormats) {
                    try {
                        LocalDate date = LocalDate.parse(input, formatter);
                        long epochSeconds = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                        return toTimePoint(epochSeconds);
                    } catch (DateTimeParseException ignore) {
                        // try next format
                    }
                }
            }

            throw new IllegalArgumentException(
                    "time must be number timestamp or datetime string (e.g. 2026-04-07 10:20:30 / 2026-04-07T10:20:30Z)");
        }

        private static List<TimePoint> parseTimePointListOrSingle(Object time) {
            List<Object> rawList = toRawListOrSingle(time, "time");
            List<TimePoint> result = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                result.add(parseTimePoint(item));
            }
            return result;
        }

        private static List<Object> parseValueListOrSingle(Object value) {
            List<Object> rawList = toRawListOrSingle(value, "value");
            List<Object> result = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                result.add(normalizeTemporalValue(item));
            }
            return result;
        }

        private static List<Object> toRawListOrSingle(Object input, String argName) {
            if (input == null) {
                throw new IllegalArgumentException(argName + " cannot be null");
            }
            if (input instanceof List<?>) {
                List<?> list = (List<?>) input;
                if (list.isEmpty()) {
                    throw new IllegalArgumentException(argName + " list cannot be empty");
                }
                return new ArrayList<>(list);
            }
            return List.of(input);
        }

        private static Object normalizeTemporalValue(Object value) {
            if (value == null) return null;
            if (value instanceof Integer || value instanceof Float || value instanceof String) {
                return value;
            }
            if (value instanceof Double) {
                return ((Double) value).floatValue();
            }
            if (value instanceof Long) {
                return Math.toIntExact((Long) value);
            }
            throw new IllegalArgumentException("value must be numeric or string or null, got: " + value.getClass().getName());
        }

        boolean isRange() {
            return end != null;
        }
    }

    public static class MutateResult {
        public long count;

        public MutateResult(long count) {
            this.count = count;
        }
    }

    public static class TemporalTriple {
        public long timeBegin;
        public long timeEnd;
        public Object value;

        public TemporalTriple(long timeBegin, long timeEnd, Object value) {
            this.timeBegin = timeBegin;
            this.timeEnd = timeEnd;
            this.value = value;
        }
    }

    public static class GetTPResult {
        public Object result;

        public GetTPResult(Object result) {
            this.result = result;
        }
    }

}
