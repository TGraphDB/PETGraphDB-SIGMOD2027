package org.neo4j.kernel.temporal;

import java.io.*;
import java.util.ArrayList;

public class Command {
    public static final int DELETE = 0;
    public static final int CREATE = 1;
    public static final int SET_TEMPORAL_PROPERTY = 2;
    public static final int START = 3;
    public static final int EMPTY = 4;

    public static final int NODE = 0;
    public static final int RELATIONSHIP = 1;
    public static final int INVALID = -1;


    private final int operation;
    private final int entityType;
    // 如果是node则仅使用nodeId，如果是relationship则仅使用startId和endId
    private final int nodeId, startId, endId;
    private final String key;
    private final long start, end;
    private final Object value;

    private Command(int operation, int entityType, int nodeId, int startId, int endId, String key, long start, long end, Object value) {
        this.operation = operation;
        this.entityType = entityType;
        this.nodeId = nodeId;
        this.startId = startId;
        this.endId = endId;
        this.key = key;
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public static Command emptyCommand() {
        return new Command(EMPTY, INVALID, -1, -1, -1, null, -1, -1, null);
    }

    public static Command startCommand() {
        return new Command(START, INVALID, -1, -1, -1, null, -1, -1, null);
    }

    public static Command nodeCreate(int id) {
        return new Command(CREATE, NODE, id, -1, -1, null, -1, -1, null);
    }

    public static Command relationShipCreate(int startId, int endId) {
        return new Command(CREATE, RELATIONSHIP, -1, startId, endId, null, -1, -1, null);
    }

    public static Command nodeDelete(int id) {
        return new Command(DELETE, NODE, id, -1, -1, null, -1, -1, null);
    }

    public static Command relationshipDelete(int startId, int endId) {
        return new Command(DELETE, RELATIONSHIP, -1, startId, endId, null, -1, -1, null);
    }

    public static Command setTemporalProperty(Command tempCommand, String key, long start, long end, Object value) {
        return new Command(tempCommand.getOperation(), tempCommand.getEntityType(), tempCommand.getNodeId(),
                tempCommand.getStartId(), tempCommand.getEndId(), key, start, end, value);
    }

    public static Command tempCommandForNodeSetTemporalProperty(int id) {
        return new Command(SET_TEMPORAL_PROPERTY, NODE, id, -1, -1, null, -1, -1, null);
    }

    public static Command tempCommandForRelationshipSetTemporalProperty(int startId, int endId) {
        return new Command(SET_TEMPORAL_PROPERTY, RELATIONSHIP, -1, startId, endId, null, -1, -1, null);
    }

    public static class CommandTransporter {
        private final ArrayList<Command> pushedCommand = new ArrayList<>();
        private int getCommandIndex = 0;
        private OutputStream outputStream = null;
        private InputStream inputStream = null;

        public CommandTransporter(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public CommandTransporter(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public void set(Command command) {
            if (outputStream == null) {
                return;
            }
            if (!command.valid()) {
                throw new RuntimeException("command is not valid!");
            }
            try {
                setInt(outputStream, command.getOperation());
                setInt(outputStream, command.getEntityType());
                setInt(outputStream, command.getNodeId());
                setInt(outputStream, command.getStartId());
                setInt(outputStream, command.getEndId());
                setString(outputStream, command.getKey());
                setLong(outputStream, command.getStart());
                setLong(outputStream, command.getEnd());
                setValue(outputStream, command.getValue());
                outputStream.flush();
                pushedCommand.add(command);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Command get() {
            if (inputStream == null) {
                return null;
            }
            try {
                return new Command(getInt(inputStream),
                        getInt(inputStream),
                        getInt(inputStream),
                        getInt(inputStream),
                        getInt(inputStream),
                        getString(inputStream),
                        getLong(inputStream),
                        getLong(inputStream),
                        getValue(inputStream));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Command getCommand() {
            while (pushedCommand.size() == getCommandIndex) {}
            Command command = pushedCommand.get(getCommandIndex);
            getCommandIndex++;
            return command;
        }

        private int getInt(InputStream inputStream) throws IOException {
            byte[] bytes = inputStream.readNBytes(4);
            return Byte.toUnsignedInt(bytes[0]) | (Byte.toUnsignedInt(bytes[1]) << 8) |
                    (Byte.toUnsignedInt(bytes[2]) << 16) | (Byte.toUnsignedInt(bytes[3]) << 24);
        }

        private void setInt(OutputStream outputStream, int integer) throws IOException {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                bytes[i] = (byte) (integer & 0xff);
                integer >>= 8;
            }
            outputStream.write(bytes);
        }

        private long getLong(InputStream inputStream) throws IOException {
            int low = getInt(inputStream), high = getInt(inputStream);
            return (((long) low) & 0xffffffffL) | (((long) high) << 32);
        }

        private void setLong(OutputStream outputStream, long l) throws IOException {
            setInt(outputStream, (int) (l & 0xffffffffL));
            l >>= 32;
            setInt(outputStream, (int) l);
        }

        private float getFloat(InputStream inputStream) throws IOException {
            return Float.intBitsToFloat(getInt(inputStream));
        }

        private void setFloat(OutputStream outputStream, float f) throws IOException {
            setInt(outputStream, Float.floatToIntBits(f));
        }

        private double getDouble(InputStream inputStream) throws IOException {
            return Double.longBitsToDouble(getLong(inputStream));
        }

        private void setDouble(OutputStream outputStream, double d) throws IOException {
            setLong(outputStream, Double.doubleToLongBits(d));
        }

        private String getString(InputStream inputStream) throws IOException {
            byte[] notNull = inputStream.readNBytes(1);
            if (notNull[0] == 0) {
                return null;
            }
            int length = getInt(inputStream);
            byte[] result = inputStream.readNBytes(length);
            return new String(result);
        }

        private void setString(OutputStream outputStream, String string) throws IOException {
            if (string == null) {
                outputStream.write(new byte[]{0});
                return;
            }
            else {
                outputStream.write(new byte[]{1});
            }
            int length = string.length();
            setInt(outputStream, length);
            outputStream.write(string.getBytes());
        }

        private Object getValue(InputStream inputStream) throws IOException {
            byte[] notNull = inputStream.readNBytes(1);
            if (notNull[0] == 0) {
                return null;
            }
            byte[] type = inputStream.readNBytes(1);
            if (type[0] == 0) {
                return getInt(inputStream);
            }
            else if (type[0] == 1) {
                return getLong(inputStream);
            }
            else if (type[0] == 2) {
                return getFloat(inputStream);
            }
            else if (type[0] == 3) {
                return getDouble(inputStream);
            }
            else if (type[0] == 4) {
                return getString(inputStream);
            }
            else {
                throw new IOException();
            }
        }

        private void setValue(OutputStream outputStream, Object o) throws IOException {
            if (o == null) {
                outputStream.write(new byte[]{0});
                return;
            }
            else {
                outputStream.write(new byte[]{1});
            }
            if (o instanceof Integer) {
                outputStream.write(new byte[]{0});
                setInt(outputStream, (int) o);
            }
            else if (o instanceof Long) {
                outputStream.write(new byte[]{1});
                setLong(outputStream, (long) o);
            }
            else if (o instanceof Float) {
                outputStream.write(new byte[]{2});
                setFloat(outputStream, (float) o);
            }
            else if (o instanceof Double) {
                outputStream.write(new byte[]{3});
                setDouble(outputStream, (double) o);
            }
            else if (o instanceof String) {
                outputStream.write(new byte[]{4});
                setString(outputStream, (String) o);
            }
            else {
                throw new IOException();
            }
        }
    }

    public int getOperation() {
        return operation;
    }

    public int getEntityType() {
        return entityType;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getStartId() {
        return startId;
    }

    public int getEndId() {
        return endId;
    }

    public String getKey() {
        return key;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public Object getValue() {
        return value;
    }

    private boolean valid() {
        if (operation == START || operation == EMPTY) {
            return true;
        }
        if (operation == INVALID || entityType == INVALID) {
            return false;
        }
        return operation != SET_TEMPORAL_PROPERTY || key != null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(operationString()).append(" ");
        if (operation == START || operation == EMPTY) {
            return builder.toString();
        }
        if (entityType == NODE) {
            builder.append("(").append(nodeId).append(")");
        }
        else if (entityType == RELATIONSHIP) {
            builder.append("(").append(startId).append(")-[]->(").append(endId).append(")");
        }
        builder.append(" ");
        if (operation != SET_TEMPORAL_PROPERTY) {
            return builder.toString();
        }
        builder.append(key).append("(");
        builder.append(start).append("~").append(end).append("):");
        builder.append(parseValue());
        return builder.toString();
    }

    public String operationString() {
        if (operation == DELETE) {
            return "delete";
        }
        else if (operation == CREATE) {
            return "create";
        }
        else if (operation == SET_TEMPORAL_PROPERTY) {
            return "set temporal property";
        }
        else if (operation == START) {
            return "start";
        }
        else if (operation == EMPTY) {
            return "empty";
        }
        throw new RuntimeException();
    }

    public String parseValue() {
        if (value == null) {
            return "null";
        }
        if (value instanceof Integer) {
            return Integer.toHexString((int) value);
        }
        else if (value instanceof Long) {
            return Long.toHexString((long) value);
        }
        else {
            return value.toString();
        }
    }
}
