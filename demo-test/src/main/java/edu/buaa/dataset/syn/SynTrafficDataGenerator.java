package edu.buaa.dataset.syn;

import edu.buaa.common.utils.SynGenerateSchema;
import edu.buaa.dataset.SynDataGenerator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traffic-aware synthetic generator.
 *
 * Compared to random temporal updates, this generator models vehicles driving on roads.
 * Edge temporal properties are produced from driving events:
 * - travel-time: measured from vehicle enter-time and leave-time on a road
 * - vehicle-cnt: current number of vehicles on the road
 * - jam-status: congestion level score bucket, computed by density/speed/recent travel time
 */
public class SynTrafficDataGenerator extends SynDataGenerator {

    private static final String PROP_TRAVEL_TIME = "travel-time";
    private static final String PROP_VEHICLE_CNT = "vehicle-cnt";
    private static final String PROP_JAM_STATUS = "jam-status";

    private static final int JAM_SMOOTH = 0;
    private static final int JAM_SLOW = 1;
    private static final int JAM_HEAVY = 2;

    public SynTrafficDataGenerator(File dataRoot, SynGenerateSchema schema) {
        super(dataRoot, schema);
    }

    @Override
    public File prepareStaticCSV(boolean isNode) throws IOException {
        if (isNode) {
            return super.prepareStaticCSV(true);
        }
        return prepareTrafficEdgeStaticCSV();
    }

    @Override
    public File prepareTPCSV(boolean isNode) throws IOException {
        if (isNode) {
            return super.prepareTPCSV(true);
        }
        return prepareTrafficEdgeTPCSV();
    }

    private File prepareTrafficEdgeStaticCSV() throws IOException {
        File fOut = new File(dir, "edge.csv");
        int edgeCnt = schema.getRel().getCnt();
        int nodeCnt = schema.getNode().getCnt();
        Random rand = ThreadLocalRandom.current();
        try (OutputStreamWriterEx writer = new OutputStreamWriterEx(new FileOutputStream(fOut))) {
            writer.writeLine("u_sid,r_from,r_to");
            for (int i = 1; i <= edgeCnt; i++) {
                int from = rand.nextInt(nodeCnt) + 1;
                int to = rand.nextInt(nodeCnt) + 1;
                if (from == to) {
                    to = (to % nodeCnt) + 1;
                }
                writer.writeLine(i + "," + from + "," + to);
            }
        }
        return fOut;
    }

    private File prepareTrafficEdgeTPCSV() throws IOException {
        SynGenerateSchema.PropertySchema relSchema = schema.getRel();
        if (relSchema.getCnt() == 0) {
            return super.prepareEmptyTPCSV(false);
        }
        File fOut = new File(dir, "edge_tp.csv");

        TrafficConfig cfg = TrafficConfig.from(schema);
        TrafficContext ctx = TrafficContext.create(schema, cfg, dir);

        try (OutputStream writer = new BufferedOutputStream(new FileOutputStream(fOut))) {
            LinkedList<String> header = new LinkedList<>(relSchema.getName());
            header.addFirst("u_sid");
            header.addFirst("t");
            writer.write(String.join(",", header).getBytes(StandardCharsets.UTF_8));
            writer.write('\n');

            PriorityQueue<Event> queue = new PriorityQueue<>(Comparator.comparingInt(e -> e.time));
            for (int vid = 1; vid <= cfg.vehicleCnt; vid++) {
                Vehicle v = new Vehicle(vid);
                ctx.vehicles[vid - 1] = v;
                int t0 = schema.getStart() + ctx.rand.nextInt(Math.max(1, cfg.warmupWindow));
                queue.add(new Event(t0, EventType.ENTER, v.id, -1, -1));
            }

            ArrayList<String> out = new ArrayList<>(Q_LENGTH);
            while (!queue.isEmpty()) {
                Event e = queue.poll();
                if (e.time >= schema.getEnd()) {
                    continue;
                }
                processEvent(e, ctx, cfg, queue, out, relSchema.getName());
                if (out.size() >= Q_LENGTH) {
                    flush(out, writer);
                }
            }
            if (!out.isEmpty()) {
                flush(out, writer);
            }
        }
        return fOut;
    }

    private void processEvent(Event e,
                              TrafficContext ctx,
                              TrafficConfig cfg,
                              PriorityQueue<Event> queue,
                              ArrayList<String> out,
                              List<String> propOrder) {
        Vehicle vehicle = ctx.vehicles[e.vehicleId - 1];
        if (e.type == EventType.ENTER) {
            Road road = e.roadId > 0 ? ctx.roads[e.roadId - 1] : pickRoad(ctx);
            vehicle.roadId = road.id;
            vehicle.enterTime = e.time;

            float densityBefore = safeDensity(road.vehicleCnt, road.capacity);
            float speed = sampleEnterSpeed(road, densityBefore, ctx.rand);
            vehicle.speed = speed;
            road.vehicleCnt++;
            road.vehicleIds.add(vehicle.id);
            road.speedSum += speed;
            road.speedCnt++;

            int travelTime = estimateTravelTime(road.lengthMeter, speed, cfg.timeUnitSecond);
            road.lastPredictedTravelTime = travelTime;
            vehicle.leaveTime = e.time + Math.max(1, travelTime);
            vehicle.leaveSeq++;
            queue.add(new Event(vehicle.leaveTime, EventType.LEAVE, vehicle.id, road.id, vehicle.leaveSeq));

            maybePropagateJam(road, e.time, ctx, cfg, queue);
            int jamStatus = computeJamStatus(road, e.time, cfg);
            out.add(toCsvRow(e.time, road.id, 0, road.vehicleCnt, jamStatus, propOrder));
            return;
        }

        if (e.leaveSeq != vehicle.leaveSeq) {
            return;
        }

        if (vehicle.roadId <= 0) {
            return;
        }

        Road road = ctx.roads[vehicle.roadId - 1];
        int travelTime = Math.max(1, e.time - vehicle.enterTime);

        road.vehicleCnt = Math.max(0, road.vehicleCnt - 1);
        road.vehicleIds.remove(vehicle.id);
        road.speedSum = Math.max(0f, road.speedSum - vehicle.speed);
        road.speedCnt = Math.max(0, road.speedCnt - 1);
        addRecentExit(road, e.time, travelTime);

        maybePropagateJam(road, e.time, ctx, cfg, queue);
        int jamStatus = computeJamStatus(road, e.time, cfg);
        out.add(toCsvRow(e.time, road.id, travelTime, road.vehicleCnt, jamStatus, propOrder));

        vehicle.roadId = -1;
        vehicle.leaveTime = -1;
        int minGap = Math.max(1, cfg.minInterRoadGap);
        int maxGap = Math.max(minGap, cfg.maxInterRoadGap);
        int gap = minGap + ctx.rand.nextInt(maxGap - minGap + 1);
        int nextRoadId = pickRoad(ctx).id;
        queue.add(new Event(e.time + gap, EventType.ENTER, vehicle.id, nextRoadId, -1));
    }

    private void maybePropagateJam(Road road,
                                   int currentT,
                                   TrafficContext ctx,
                                   TrafficConfig cfg,
                                   PriorityQueue<Event> queue) {
        float density = safeDensity(road.vehicleCnt, road.capacity);
        if (density < cfg.propagateDensityThreshold) {
            return;
        }
        if (currentT - road.lastPropagationTime < cfg.propagateCooldownStep) {
            return;
        }
        road.lastPropagationTime = currentT;
        applySpeedDropOnRoad(road, cfg.selfRoadSpeedDropFactor, currentT, ctx, queue, cfg);
        List<Integer> upstreamRoadIds = ctx.incomingRoadsByNode.get(road.fromNode);
        if (upstreamRoadIds == null) {
            return;
        }
        for (Integer rid : upstreamRoadIds) {
            if (rid == road.id) {
                continue;
            }
            Road upstream = ctx.roads[rid - 1];
            applySpeedDropOnRoad(upstream, cfg.upstreamRoadSpeedDropFactor, currentT, ctx, queue, cfg);
        }
    }

    private void applySpeedDropOnRoad(Road road,
                                      float dropFactor,
                                      int currentT,
                                      TrafficContext ctx,
                                      PriorityQueue<Event> queue,
                                      TrafficConfig cfg) {
        if (road.vehicleIds.isEmpty()) {
            return;
        }
        float factor = clamp(dropFactor, cfg.minSpeedFactor, 1f);
        if (factor >= 0.999f) {
            return;
        }

        ArrayList<Integer> ids = new ArrayList<>(road.vehicleIds);
        for (Integer vid : ids) {
            Vehicle v = ctx.vehicles[vid - 1];
            if (v.roadId != road.id || v.leaveTime <= currentT) {
                continue;
            }

            float oldSpeed = v.speed;
            float newSpeed = Math.max(5f, oldSpeed * factor);
            if (newSpeed >= oldSpeed) {
                continue;
            }

            v.speed = newSpeed;
            road.speedSum += (newSpeed - oldSpeed);

            int remain = Math.max(1, v.leaveTime - currentT);
            int newRemain = Math.max(1, (int) Math.ceil(remain / factor));
            if (newRemain > remain) {
                v.leaveTime = currentT + newRemain;
                v.leaveSeq++;
                queue.add(new Event(v.leaveTime, EventType.LEAVE, v.id, road.id, v.leaveSeq));
            }
        }
    }

    private String toCsvRow(int t,
                            int roadId,
                            int travelTime,
                            int vehicleCnt,
                            int jamStatus,
                            List<String> propOrder) {
        String[] val = new String[propOrder.size() + 2];
        val[0] = Integer.toString(t);
        val[1] = Integer.toString(roadId);
        for (int i = 0; i < propOrder.size(); i++) {
            String p = propOrder.get(i);
            if (PROP_TRAVEL_TIME.equals(p)) {
                val[i + 2] = Integer.toString(travelTime);
            } else if (PROP_VEHICLE_CNT.equals(p)) {
                val[i + 2] = Integer.toString(vehicleCnt);
            } else if (PROP_JAM_STATUS.equals(p)) {
                val[i + 2] = Integer.toString(jamStatus);
            } else {
                val[i + 2] = "0";
            }
        }
        return String.join(",", val);
    }

    private void addRecentExit(Road road, int currentT, int travelTime) {
        road.recentExit.addLast(new ExitSample(currentT, travelTime));
        road.recentTravelSum += travelTime;
    }

    private int computeJamStatus(Road road, int currentT, TrafficConfig cfg) {
        int lookback = Math.max(1, cfg.jamLookbackMinute * 60 / cfg.timeUnitSecond);
        while (!road.recentExit.isEmpty()) {
            ExitSample first = road.recentExit.peekFirst();
            if (currentT - first.exitTime <= lookback) {
                break;
            }
            road.recentTravelSum -= first.travelTime;
            road.recentExit.pollFirst();
        }

        float density = safeDensity(road.vehicleCnt, road.capacity);
        float avgSpeed = road.speedCnt == 0 ? road.speedLimitKmh : (road.speedSum / road.speedCnt);
        float speedScore = 1f - clamp(avgSpeed / road.speedLimitKmh, 0f, 1f);

        float freeFlowT = estimateTravelTime(road.lengthMeter, road.speedLimitKmh, cfg.timeUnitSecond);
        float recentAvgTravel = road.recentExit.isEmpty() ? road.lastPredictedTravelTime
                : (float) road.recentTravelSum / road.recentExit.size();
        float delayRatio = freeFlowT <= 0 ? 1f : recentAvgTravel / freeFlowT;
        float delayScore = clamp((delayRatio - 1f) / 1.5f, 0f, 1f);

        float score = 0.5f * clamp(density, 0f, 1.2f) + 0.3f * speedScore + 0.2f * delayScore;
        if (score < 0.33f) {
            return JAM_SMOOTH;
        }
        if (score < 0.66f) {
            return JAM_SLOW;
        }
        return JAM_HEAVY;
    }

    private static float sampleEnterSpeed(Road road, float density, Random rand) {
        float jamPenalty = 1f - clamp(density, 0f, 1f) * 0.65f;
        float randomFactor = 0.75f + rand.nextFloat() * 0.3f;
        float speed = road.speedLimitKmh * jamPenalty * randomFactor;
        return Math.max(5f, Math.min(speed, road.speedLimitKmh));
    }

    private static float safeDensity(int vehicleCnt, int capacity) {
        if (capacity <= 0) {
            return 1f;
        }
        return (float) vehicleCnt / (float) capacity;
    }

    private static float clamp(float val, float low, float high) {
        return Math.max(low, Math.min(high, val));
    }

    private static int estimateTravelTime(int lengthMeter, float speedKmh, int timeUnitSecond) {
        float meterPerSecond = speedKmh / 3.6f;
        float sec = lengthMeter / Math.max(1f, meterPerSecond);
        return Math.max(1, Math.round(sec / Math.max(1, timeUnitSecond)));
    }

    private static void flush(ArrayList<String> out, OutputStream writer) throws IOException {
        writer.write(String.join("\n", out).getBytes(StandardCharsets.UTF_8));
        writer.write('\n');
        out.clear();
    }

    private static Road pickRoad(TrafficContext ctx) {
        int idx = ctx.rand.nextInt(ctx.roads.length);
        return ctx.roads[idx];
    }

    private static class TrafficContext {
        private final Road[] roads;
        private final Vehicle[] vehicles;
        private final Map<Integer, List<Integer>> incomingRoadsByNode;
        private final Random rand;

        private TrafficContext(Road[] roads,
                               Vehicle[] vehicles,
                               Map<Integer, List<Integer>> incomingRoadsByNode,
                               Random rand) {
            this.roads = roads;
            this.vehicles = vehicles;
            this.incomingRoadsByNode = incomingRoadsByNode;
            this.rand = rand;
        }

        private static TrafficContext create(SynGenerateSchema schema, TrafficConfig cfg) {
            return create(schema, cfg, null);
        }

        private static TrafficContext create(SynGenerateSchema schema, TrafficConfig cfg, File dataDir) {
            Random rand = ThreadLocalRandom.current();
            int roadCnt = schema.getRel().getCnt();
            int nodeCnt = schema.getNode().getCnt();
            int[][] endpoint = loadRoadEndpoint(dataDir, roadCnt, nodeCnt, rand);
            Road[] roads = new Road[roadCnt];
            Map<Integer, List<Integer>> incomingRoadsByNode = new HashMap<>();
            for (int i = 1; i <= roadCnt; i++) {
                RoadLevel level = RoadLevel.pick(rand);
                int len = cfg.minRoadLengthMeter + rand.nextInt(cfg.maxRoadLengthMeter - cfg.minRoadLengthMeter + 1);
                int cap = Math.max(3, level.laneCnt * Math.max(1, len / cfg.lengthPerVehicleMeter));
                int from = endpoint[i - 1][0];
                int to = endpoint[i - 1][1];
                roads[i - 1] = new Road(i, from, to, level, len, cap);
                incomingRoadsByNode.computeIfAbsent(to, k -> new ArrayList<>()).add(i);
            }
            Vehicle[] vehicles = new Vehicle[cfg.vehicleCnt];
            return new TrafficContext(roads, vehicles, incomingRoadsByNode, rand);
        }

        private static int[][] loadRoadEndpoint(File dataDir, int roadCnt, int nodeCnt, Random rand) {
            int[][] endpoint = new int[roadCnt][2];
            boolean loaded = false;
            if (dataDir != null) {
                File edgeFile = new File(dataDir, "edge.csv");
                if (edgeFile.exists() && edgeFile.isFile()) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(new java.io.FileInputStream(edgeFile), StandardCharsets.UTF_8))) {
                        reader.readLine();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] arr = line.split(",");
                            if (arr.length < 3) {
                                continue;
                            }
                            int id = Integer.parseInt(arr[0]);
                            if (id <= 0 || id > roadCnt) {
                                continue;
                            }
                            int from = Integer.parseInt(arr[1]);
                            int to = Integer.parseInt(arr[2]);
                            endpoint[id - 1][0] = from;
                            endpoint[id - 1][1] = to;
                            loaded = true;
                        }
                    } catch (Exception ignore) {
                        loaded = false;
                    }
                }
            }

            for (int i = 0; i < roadCnt; i++) {
                if (endpoint[i][0] <= 0 || endpoint[i][1] <= 0 || endpoint[i][0] == endpoint[i][1]) {
                    endpoint[i][0] = rand.nextInt(nodeCnt) + 1;
                    endpoint[i][1] = rand.nextInt(nodeCnt) + 1;
                    if (endpoint[i][1] == endpoint[i][0]) {
                        endpoint[i][1] = (endpoint[i][1] % nodeCnt) + 1;
                    }
                }
            }
            if (!loaded) {
                return endpoint;
            }
            return endpoint;
        }
    }

    private enum EventType {
        ENTER,
        LEAVE
    }

    private static class Event {
        private final int time;
        private final EventType type;
        private final int vehicleId;
        private final int roadId;
        private final int leaveSeq;

        private Event(int time, EventType type, int vehicleId, int roadId, int leaveSeq) {
            this.time = time;
            this.type = type;
            this.vehicleId = vehicleId;
            this.roadId = roadId;
            this.leaveSeq = leaveSeq;
        }
    }

    private static class ExitSample {
        private final int exitTime;
        private final int travelTime;

        private ExitSample(int exitTime, int travelTime) {
            this.exitTime = exitTime;
            this.travelTime = travelTime;
        }
    }

    private static class Vehicle {
        private final int id;
        private int roadId;
        private int enterTime;
        private float speed;
        private int leaveTime;
        private int leaveSeq;

        private Vehicle(int id) {
            this.id = id;
            this.roadId = -1;
            this.enterTime = -1;
            this.speed = 0f;
            this.leaveTime = -1;
            this.leaveSeq = 0;
        }
    }

    private static class Road {
        private final int id;
        private final int fromNode;
        private final int toNode;
        private final int laneCnt;
        private final float speedLimitKmh;
        private final int lengthMeter;
        private final int capacity;

        private int vehicleCnt;
        private final Set<Integer> vehicleIds;
        private float speedSum;
        private int speedCnt;
        private final ArrayDeque<ExitSample> recentExit;
        private long recentTravelSum;
        private int lastPredictedTravelTime;
        private int lastPropagationTime;

        private Road(int id, int fromNode, int toNode, RoadLevel level, int lengthMeter, int capacity) {
            this.id = id;
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.laneCnt = level.laneCnt;
            this.speedLimitKmh = level.speedLimitKmh;
            this.lengthMeter = lengthMeter;
            this.capacity = capacity;
            this.vehicleCnt = 0;
            this.vehicleIds = new HashSet<>();
            this.speedSum = 0f;
            this.speedCnt = 0;
            this.recentExit = new ArrayDeque<>();
            this.recentTravelSum = 0;
            this.lastPredictedTravelTime = Math.max(1, estimateTravelTime(lengthMeter, speedLimitKmh, 1));
            this.lastPropagationTime = Integer.MIN_VALUE / 2;
        }
    }

    private enum RoadLevel {
        EXPRESS(4, 100f, 0.15f),
        ARTERIAL(3, 70f, 0.35f),
        COLLECTOR(2, 50f, 0.35f),
        LOCAL(1, 35f, 0.15f);

        private final int laneCnt;
        private final float speedLimitKmh;
        private final float prob;

        RoadLevel(int laneCnt, float speedLimitKmh, float prob) {
            this.laneCnt = laneCnt;
            this.speedLimitKmh = speedLimitKmh;
            this.prob = prob;
        }

        static RoadLevel pick(Random rand) {
            float r = rand.nextFloat();
            float acc = 0f;
            for (RoadLevel lvl : values()) {
                acc += lvl.prob;
                if (r <= acc) {
                    return lvl;
                }
            }
            return LOCAL;
        }
    }

    private static class TrafficConfig {
        private final int vehicleCnt;
        private final int warmupWindow;
        private final int minInterRoadGap;
        private final int maxInterRoadGap;
        private final int minRoadLengthMeter;
        private final int maxRoadLengthMeter;
        private final int lengthPerVehicleMeter;
        private final int jamLookbackMinute;
        private final int timeUnitSecond;
        private final float propagateDensityThreshold;
        private final float selfRoadSpeedDropFactor;
        private final float upstreamRoadSpeedDropFactor;
        private final float minSpeedFactor;
        private final int propagateCooldownStep;

        private TrafficConfig(int vehicleCnt,
                              int warmupWindow,
                              int minInterRoadGap,
                              int maxInterRoadGap,
                              int minRoadLengthMeter,
                              int maxRoadLengthMeter,
                              int lengthPerVehicleMeter,
                              int jamLookbackMinute,
                              int timeUnitSecond,
                              float propagateDensityThreshold,
                              float selfRoadSpeedDropFactor,
                              float upstreamRoadSpeedDropFactor,
                              float minSpeedFactor,
                              int propagateCooldownStep) {
            this.vehicleCnt = vehicleCnt;
            this.warmupWindow = warmupWindow;
            this.minInterRoadGap = minInterRoadGap;
            this.maxInterRoadGap = maxInterRoadGap;
            this.minRoadLengthMeter = minRoadLengthMeter;
            this.maxRoadLengthMeter = maxRoadLengthMeter;
            this.lengthPerVehicleMeter = lengthPerVehicleMeter;
            this.jamLookbackMinute = jamLookbackMinute;
            this.timeUnitSecond = timeUnitSecond;
            this.propagateDensityThreshold = propagateDensityThreshold;
            this.selfRoadSpeedDropFactor = selfRoadSpeedDropFactor;
            this.upstreamRoadSpeedDropFactor = upstreamRoadSpeedDropFactor;
            this.minSpeedFactor = minSpeedFactor;
            this.propagateCooldownStep = propagateCooldownStep;
        }

        private static TrafficConfig from(SynGenerateSchema schema) {
            int edgeCnt = Math.max(1, schema.getRel().getCnt());
            int defaultVehicle = Math.max(2000, edgeCnt / 2);
            int step = Math.max(1, schema.getRel().getStep());
            int defaultWarmup = Math.max(step, step * 2);
            return new TrafficConfig(
                    intProp("syn.traffic.vehicleCnt", defaultVehicle),
                    intProp("syn.traffic.warmupWindow", defaultWarmup),
                    intProp("syn.traffic.minInterRoadGap", 1),
                    intProp("syn.traffic.maxInterRoadGap", Math.max(2, step / 2)),
                    intProp("syn.traffic.minRoadLengthMeter", 100),
                    intProp("syn.traffic.maxRoadLengthMeter", 3000),
                    intProp("syn.traffic.lengthPerVehicleMeter", 8),
                    intProp("syn.traffic.jamLookbackMinute", 5),
                    intProp("syn.traffic.timeUnitSecond", 1),
                    floatProp("syn.traffic.propagateDensityThreshold", 0.8f),
                    floatProp("syn.traffic.selfRoadSpeedDropFactor", 0.80f),
                    floatProp("syn.traffic.upstreamRoadSpeedDropFactor", 0.90f),
                    floatProp("syn.traffic.minSpeedFactor", 0.35f),
                    intProp("syn.traffic.propagateCooldownStep", Math.max(10, step / 3))
            );
        }

        private static int intProp(String key, int defaultVal) {
            String val = System.getProperty(key);
            if (val == null || val.trim().isEmpty()) {
                return defaultVal;
            }
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }

        private static float floatProp(String key, float defaultVal) {
            String val = System.getProperty(key);
            if (val == null || val.trim().isEmpty()) {
                return defaultVal;
            }
            try {
                return Float.parseFloat(val.trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
    }

    private static class OutputStreamWriterEx implements AutoCloseable {
        private final OutputStream out;

        private OutputStreamWriterEx(FileOutputStream fos) {
            this.out = new BufferedOutputStream(fos);
        }

        private void writeLine(String line) throws IOException {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}