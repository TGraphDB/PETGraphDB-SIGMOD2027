package org.neo4j.kernel.temporal;

import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TimePoint;

import java.io.OutputStream;
import java.util.*;

public class GraphForTestValidation {
    public static final Label nodeLabel = Label.label("test");
    public static final RelationshipType relationshipType = RelationshipType.withName("test");
    private static final int propertyKeyRange = 5;
    private static final int timeRange = 20;
    private static final HashMap<Integer, Class<?>> classes = new HashMap<>();
    private static final int allowedTypeNum = 5;

    static {
        classes.put(0, Integer.class);
        classes.put(1, Long.class);
        classes.put(2, Double.class);
        classes.put(3, Float.class);
        classes.put(4, String.class);
    }

    private int createdNodeNum;
    private final HashSet<Integer> removedNodeIds = new HashSet<>();
    private int relationshipNum;
    private final boolean singleNode;
    // list中每个元素都是两层map，第一层key为属性名，第二层key为时间。list的下标即为点id，值为点的时态属性情况
    private final ArrayList<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>> nodes = new ArrayList<>();
    // 双层list的元素含义同nodes，relationships.get(i).get(j)表示的是id为i的点指向id为j的点的关系的时态属性，若为null表示不存在该关系
    private final ArrayList<ArrayList<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>>> relationships = new ArrayList<>();
    private Command.CommandTransporter transporter = new Command.CommandTransporter((OutputStream) null);
    public void setTransporter(Command.CommandTransporter transporter) {
        this.transporter = transporter;
    }

    public Command.CommandTransporter getTransporter() {
        return transporter;
    }

    private final ArrayList<Command> initialCommands = new ArrayList<>();

    public GraphForTestValidation(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder, boolean singleNode) {
        // 1、首先随机确定点数
        this.singleNode = singleNode;
        if (!singleNode) {
            createdNodeNum = dataGeneratorRecorder.nextInt(18) + 2;
        }
        else {
            createdNodeNum = 1;
        }
        // 2、然后随机确定初始边数，为最大边数的20%~40%
        relationshipNum = (int) (((double) dataGeneratorRecorder.nextInt(2) + 2) / 10 * (createdNodeNum - 1) * createdNodeNum);
        // 3、分别构建数据库中的点和外部验证的点
        // 3.1、构建数据库中的点
        for (int i = 0; i < createdNodeNum; i++) {
            Node node = transaction.createNode(nodeLabel);
            node.setProperty("id", i);
            initialCommands.add(Command.nodeCreate(i));
        }
        // 3.2、构建外部验证的点，ArrayList中的元素表示所有的时态属性，外层key为时态属性名，里层key为时间，value为值
        for (int i = 0; i < createdNodeNum; i++) {
            nodes.add(new HashMap<>());
        }
        // 4、分别构建数据库中的边和外部的边
        // 4.1、构建数据库中的边
        ArrayList<Pair<Integer, Integer>> selectedRelationships = selectRelationshipsForCreate(dataGeneratorRecorder);
        for (Pair<Integer, Integer> selectedRelationship : selectedRelationships) {
            Node start = transaction.findNode(nodeLabel, "id", selectedRelationship.getLeft());
            Node end = transaction.findNode(nodeLabel, "id", selectedRelationship.getRight());
            start.createRelationshipTo(end, relationshipType);
            initialCommands.add(Command.relationShipCreate(selectedRelationship.getLeft(), selectedRelationship.getRight()));
        }
        // 4.2、构建外部验证的边
        for (int i = 0; i < createdNodeNum; i++) {
            ArrayList<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>> relationshipsOfOneNode = new ArrayList<>();
            for (int j = 0; j < createdNodeNum; j++) {
                relationshipsOfOneNode.add(null);
            }
            relationships.add(relationshipsOfOneNode);
        }
        for (Pair<Integer, Integer> selectedRelationship : selectedRelationships) {
            relationships.get(selectedRelationship.getLeft()).set(selectedRelationship.getRight(), new HashMap<>());
        }
    }

    public void flushInitialCommands() {
        for (Command command : initialCommands) {
            transporter.set(command);
        }
    }

    private ArrayList<Pair<Integer, Integer>> selectRelationshipsForCreate(DataGeneratorRecorder dataGeneratorRecorder) {
        ArrayList<Pair<Integer, Integer>> total = new ArrayList<>();
        for (int i = 0; i < createdNodeNum; i++) {
            for (int j = 0; j < createdNodeNum; j++) {
                total.add(Pair.of(i, j));
            }
        }
        ArrayList<Pair<Integer, Integer>> result = new ArrayList<>();
        for (int i = 0; i < relationshipNum; i++) {
            result.add(total.remove(dataGeneratorRecorder.nextInt(total.size())));
        }
        return result;
    }

    // 选择一个在数据库中存在的点
    private Pair<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>, Node> selectNode(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        int src = dataGeneratorRecorder.nextInt(createdNodeNum);
        boolean larger = dataGeneratorRecorder.nextBoolean();
        if (larger) {
            while (removedNodeIds.contains(src) && src < createdNodeNum) {
                src++;
            }
            if (src == createdNodeNum) {
                src--;
                while (removedNodeIds.contains(src)) {
                    src--;
                }
            }
        }
        else {
            while (removedNodeIds.contains(src) && src >= 0) {
                src--;
            }
            if (src < 0) {
                src++;
                while (removedNodeIds.contains(src)) {
                    src++;
                }
            }
        }
        return Pair.of(nodes.get(src), transaction.findNode(nodeLabel, "id", src));
    }

    // 选择方式：随机选取一个点，若没有以其为起点的关系则返回null，若有则选取其中id第i小的那个关系（i<=选择点的出度且随机）
    private Pair<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>, Relationship> selectRelationship(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        Node node = selectNode(transaction, dataGeneratorRecorder).getRight();
        int src = (int) node.getProperty("id");
        int outgoingDegreeNum = node.getDegree(Direction.OUTGOING);
        if (outgoingDegreeNum == 0) {
            return null;
        }
        int selectIndex = dataGeneratorRecorder.nextInt(outgoingDegreeNum);
        TreeMap<Integer, Relationship> relationshipTreeMap = new TreeMap<>();
        Iterable<Relationship> relationshipsOfNode = node.getRelationships(Direction.OUTGOING);
        for (Relationship relationship : relationshipsOfNode) {
            int dstId = (int) relationship.getEndNode().getProperty("id");
            relationshipTreeMap.put(dstId, relationship);
        }
        int i = 0;
        for (Map.Entry<Integer, Relationship> entry : relationshipTreeMap.entrySet()) {
            if (i == selectIndex) {
                return Pair.of(relationships.get(src).get(entry.getKey()), entry.getValue());
            }
            i++;
        }
        // 正常情况下是走不到这里的
        return null;
    }

    // 针对一个在数据库中存在的entity进行时态属性验证
    private void validateEntity(Entity entity, HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>> props) {
        Iterable<String> entityPropertyKeys = entity.getPropertyKeys();
        props = new HashMap<>(props);
        for (String key : entityPropertyKeys) {
            try {
                Integer.parseInt(key);
            }
            catch (NumberFormatException ignored) {
                continue;
            }
            assert props.containsKey(key);
            Pair<Class<?>, HashMap<Integer, Object>> temporalValueWithType = props.get(key);
            HashMap<Integer, Object> temporalValue = temporalValueWithType.getValue();
            for (int i = 0; i < timeRange; i++) {
                Object mapValue = temporalValue.get(i);
                Object databaseValue = entity.getTemporalProperty(key, new TimePoint(i));
                // 如果其中一个是null而另一个不是就错了
                assert (mapValue == null) == (databaseValue == null);
                // 都是null就可以
                if (mapValue == null) continue;
                // 否则判断一下是不是一样的
                if (databaseValue instanceof Slice) {
                    databaseValue = new String(((Slice) databaseValue).getRawArray());
                    assert mapValue instanceof String;
                    assert ((String) databaseValue).compareTo((String) mapValue) == 0;
                }
                else {
                    assert mapValue.equals(databaseValue);
                }
            }
            props.remove(key);
        }
        assert props.isEmpty();
    }

    // 删除一个点，随机选取一个点，删除与其相关的关系并验证，删除该点并验证
    public void removeNode(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        if (singleNode) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
            return;
        }
        if (createdNodeNum - removedNodeIds.size() <= 2) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
            return;
        }
        Node node = selectNode(transaction, dataGeneratorRecorder).getRight();
        Iterable<Relationship> relationshipsOfNode = node.getRelationships();
        for (Relationship relationship : relationshipsOfNode) {
            int src = (int) relationship.getStartNode().getProperty("id");
            int dst = (int) relationship.getEndNode().getProperty("id");
            validateEntity(relationship, relationships.get(src).get(dst));
            relationship.delete();
            relationships.get(src).set(dst, null);
            relationshipNum--;
        }
        int nodeId = (int) node.getProperty("id");
        validateEntity(node, nodes.get(nodeId));
        node.delete();
        nodes.set(nodeId, null);
        removedNodeIds.add(nodeId);
        transporter.set(Command.nodeDelete(nodeId));
    }

    public void addNode(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        if (singleNode) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
            return;
        }
        Node node = transaction.createNode(nodeLabel);
        node.setProperty("id", createdNodeNum);
        nodes.add(new HashMap<>());
        for (ArrayList<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>> relationshipsOfNode : relationships) {
            relationshipsOfNode.add(null);
        }
        ArrayList<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>> temp = new ArrayList<>();
        for (int i = 0; i <= createdNodeNum; i++) {
            temp.add(null);
        }
        relationships.add(temp);
        transporter.set(Command.nodeCreate(createdNodeNum));
        createdNodeNum++;
    }

    // 删除一个关系，随机选取一个关系，若选中已存在关系则验证其现有时态关系是否正确并删除，若未选中已存在关系则随机设置时态属性
    public void removeRelationship(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        if (singleNode) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
            return;
        }
        Pair<HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>>, Relationship> relationshipWrap = selectRelationship(transaction, dataGeneratorRecorder);
        if (relationshipWrap == null) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
        }
        else {
            Relationship relationship = relationshipWrap.getRight();
            int src = (int) relationship.getStartNode().getProperty("id");
            int dst = (int) relationship.getEndNode().getProperty("id");
            validateEntity(relationship, relationships.get(src).get(dst));
            relationship.delete();
            relationships.get(src).set(dst, null);
            relationshipNum--;
            transporter.set(Command.relationshipDelete(src, dst));
        }
    }

    // 增加一个关系，随机选取两个点，若已经存在关系则随机设置时态属性
    public void addRelationship(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        if (singleNode) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
            return;
        }
        Node startNode = selectNode(transaction, dataGeneratorRecorder).getRight();
        Node endNode = selectNode(transaction, dataGeneratorRecorder).getRight();
        int src = (int) startNode.getProperty("id");
        int dst = (int) endNode.getProperty("id");
        if (relationships.get(src).get(dst) != null) {
            setTemporalProperty(transaction, dataGeneratorRecorder);
        }
        else {
            relationships.get(src).set(dst, new HashMap<>());
            startNode.createRelationshipTo(endNode, relationshipType);
            relationshipNum++;
            transporter.set(Command.relationShipCreate(src, dst));
        }
    }

    // 属性名随机（0-4的字符串），时间随机（起始点和终点在[0, 20)），值随机（[0, 100)）；选边可能会选到空边，策略是重复选边，若经历nodeNum次还没有选到就加边
    public void setTemporalProperty(Transaction transaction, DataGeneratorRecorder dataGeneratorRecorder) {
        Entity entity = null;
        HashMap<String, Pair<Class<?>, HashMap<Integer, Object>>> map = null;
        Command tempCommand = null;
        if (singleNode) {
            entity = transaction.findNode(nodeLabel, "id", 0);
            map = nodes.get(0);
            tempCommand = Command.tempCommandForNodeSetTemporalProperty(0);
        }
        else {
            if (relationshipNum == 0) {
                addRelationship(transaction, dataGeneratorRecorder);
                return;
            }
            if (dataGeneratorRecorder.nextInt(relationshipNum) < createdNodeNum) {
                var pair = selectNode(transaction, dataGeneratorRecorder);
                entity = pair.getRight();
                map = pair.getKey();
                tempCommand = Command.tempCommandForNodeSetTemporalProperty((int) entity.getProperty("id"));
            }
            else {
                for (int i = 0; i < createdNodeNum; i++) {
                    var pair = selectRelationship(transaction, dataGeneratorRecorder);
                    if (pair != null) {
                        entity = pair.getRight();
                        map = pair.getKey();
                        Relationship relationship = (Relationship) entity;
                        int src = (int) relationship.getStartNode().getProperty("id");
                        int dst = (int) relationship.getEndNode().getProperty("id");
                        tempCommand = Command.tempCommandForRelationshipSetTemporalProperty(src, dst);
                        break;
                    }
                }
            }
        }
        if (entity == null) {
            addRelationship(transaction, dataGeneratorRecorder);
            return;
        }
        String key = String.valueOf(dataGeneratorRecorder.nextInt(propertyKeyRange));
        int t1 = dataGeneratorRecorder.nextInt(timeRange);
        int t2 = dataGeneratorRecorder.nextInt(timeRange);
        int start, end;
        if (t1 < t2) {
            start = t1;
            end = t2;
        }
        else {
            start = t2;
            end = t1;
        }
        assert map != null;
        Pair<Class<?>, HashMap<Integer, Object>> temporalValueWithType;
        boolean isNullValue = (dataGeneratorRecorder.nextInt(10) < 3);
        if (map.containsKey(key)) {
            temporalValueWithType = map.get(key);
        }
        else {
            // 既要写入null值，又没有已有数据，那这次写入就是非法的，不做任何操作
            if (isNullValue) {
                transporter.set(Command.emptyCommand());
                return;
            }
            HashMap<Integer, Object> temporalValue = new HashMap<>();
            Class<?> type = classes.get(dataGeneratorRecorder.nextInt(allowedTypeNum));
            temporalValueWithType = Pair.of(type, temporalValue);
            map.put(key, temporalValueWithType);
        }
        HashMap<Integer, Object> temporalValue = temporalValueWithType.getValue();
        Class<?> type = temporalValueWithType.getKey();
        Object value = null;
        if (!isNullValue) {
            if (type.equals(Integer.class)) {
                value = dataGeneratorRecorder.nextInt();
            } else if (type.equals(Long.class)) {
                value = (long) dataGeneratorRecorder.nextInt();
            } else if (type.equals(Double.class)) {
                value = dataGeneratorRecorder.nextDouble();
            } else if (type.equals(Float.class)) {
                value = (float) dataGeneratorRecorder.nextDouble();
            } else if (type.equals(String.class)) {
                value = dataGeneratorRecorder.nextString(100);
            }
        }
        for (int i = start; i <= end; i++) {
            temporalValue.put(i, value);
        }
        transporter.set(Command.setTemporalProperty(tempCommand, key, start, end, value));
        if (value != null) {
            if (value instanceof String) {
                value = new Slice(((String) value).getBytes());
            }
        }
        entity.setTemporalProperty(key, new TimePoint(start), new TimePoint(end), value);
    }

    // 调用该方法会造成验证区和数据库的不一致，是一种破坏性的验证，因此只能在测试的最后调用该方法
    public void validateAll(Transaction transaction) {
        for (int i = 0; i < createdNodeNum; i++) {
            Node node = transaction.findNode(nodeLabel, "id", i);
            if (node == null) continue;
            if (node.getDegree(Direction.OUTGOING) != 0) {
                Iterable<Relationship> relationshipIterable = node.getRelationships(Direction.OUTGOING);
                relationshipIterable.forEach(relationship -> {
                    int src = (int) relationship.getStartNode().getProperty("id");
                    int dst = (int) relationship.getEndNode().getProperty("id");
                    validateEntity(relationship, relationships.get(src).get(dst));
                    relationships.get(src).set(dst, null);
                    relationshipNum--;
                });
            }
        }
        for (int i = 0; i < createdNodeNum; i++) {
            for (int j = 0; j < createdNodeNum; j++) {
                assert relationships.get(i).get(j) == null;
            }
        }
        for (int i = 0; i < createdNodeNum; i++) {
            Node node = transaction.findNode(nodeLabel, "id", i);
            if (node == null) {
                assert nodes.get(i) == null;
                continue;
            }
            validateEntity(node, nodes.get(i));
        }
    }
}
