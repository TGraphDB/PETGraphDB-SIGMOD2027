package edu.buaa.common.utils;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.*;

public class TemporalGraphPropertySchema {
    public String name;
    //name, type
    public final Map<String, PVal.Type> nodeStatic = new LinkedHashMap<>();
    public final Map<String, PVal.Type> nodeTemporal = new LinkedHashMap<>();
    public final Map<String, PVal.Type> relStatic = new LinkedHashMap<>();
    public final Map<String, PVal.Type> relTemporal = new LinkedHashMap<>();
    public float nodeUpdate;
    public float edgeUpdate;
    public final Map<String, Float> nTpUpdate = new HashMap<>();
    public final Map<String, Float> rTpUpdate = new HashMap<>();

    public PVal.Type getType(boolean isNode, boolean isStatic, String prop){
        Map<String, PVal.Type> map;
        if(isNode && isStatic) map=nodeStatic;
        else if(isNode) map=nodeTemporal;
        else if(isStatic) map=relStatic;
        else map=relTemporal;
        return map.get(prop);
    }

    public static TemporalGraphPropertySchema load(String dataset){
        Yaml yml = new Yaml(new Constructor(GraphSchema.class));
        InputStream inputStream = TemporalGraphPropertySchema.class.getClassLoader()
                .getResourceAsStream("dataset-schema.yml");
        List<String> datasets = new ArrayList<>();
        for(Object obj : yml.loadAll(inputStream)){
            if(obj instanceof GraphSchema){
                GraphSchema g = (GraphSchema) obj;
                if(g.dataset.equals(dataset)){
                    TemporalGraphPropertySchema sg = new TemporalGraphPropertySchema();
                    conv(g.node, sg.nodeStatic, new HashMap<>());
                    conv(g.node_tp, sg.nodeTemporal, sg.nTpUpdate);
                    conv(g.rel, sg.relStatic, new HashMap<>());
                    conv(g.rel_tp, sg.relTemporal, sg.rTpUpdate);
                    sg.name = dataset;
                    sg.nodeUpdate = g.node_tp!=null ? g.node_tp.update : -1f;
                    sg.edgeUpdate = g.rel_tp!=null ? g.rel_tp.update : -1f;
                    return sg;
                }
                datasets.add(g.dataset);
//                System.out.println(JSON.toJSONString(g));
            }
        }
        throw new IllegalArgumentException("dataset not found, available: "+String.join(",", datasets)+" got "+dataset);
    }

    private static void conv(PropertySchema props, Map<String, PVal.Type> result, Map<String, Float> prop) {
        if(props==null) return;
        assert props.name.size()==props.type.size();
        int s = props.name.size();
        for(int i=0; i<s; i++){
            String type = props.type.get(i);
            String name = props.name.get(i);
            Float pUpdate = props.prop!=null ? props.prop.get(i) : -1f;
            prop.put(name, pUpdate);
            PVal.Type t;
            switch (type){
                case "int": t = PVal.Type.INT; break;
                case "str": t = PVal.Type.STRING; break;
                case "float": t= PVal.Type.FLOAT; break;
                default: throw new IllegalArgumentException("unknown type: "+type);
            }
            result.put(name, t);
        }
    }

    public static class GraphSchema{
        private String dataset;
        private PropertySchema node;
        private PropertySchema node_tp;
        private PropertySchema rel;
        private PropertySchema rel_tp;

        public PropertySchema getNode() {
            return node;
        }

        public void setNode(PropertySchema node) {
            this.node = node;
        }

        public PropertySchema getRel() {
            return rel;
        }

        public void setRel(PropertySchema rel) {
            this.rel = rel;
        }

        public PropertySchema getNode_tp() {
            return node_tp;
        }

        public void setNode_tp(PropertySchema node_tp) {
            this.node_tp = node_tp;
        }

        public PropertySchema getRel_tp() {
            return rel_tp;
        }

        public void setRel_tp(PropertySchema rel_tp) {
            this.rel_tp = rel_tp;
        }

        public String getDataset() {
            return dataset;
        }

        public void setDataset(String dataset) {
            this.dataset = dataset;
        }
    }


    public static class PropertySchema{
        private List<String> type;
        private List<String> name;
        private List<Float> prop;
        private float update;

        public List<String> getName() {
            return name;
        }

        public void setName(List<String> name) {
            this.name = name;
        }

        public List<String> getType() {
            return type;
        }

        public void setType(List<String> type) {
            this.type = type;
        }

        public List<Float> getProp() {
            return prop;
        }

        public void setProp(List<Float> prop) {
            this.prop = prop;
        }

        public float getUpdate() {
            return update;
        }

        public void setUpdate(float update) {
            this.update = update;
        }
    }
}
