package edu.buaa.common.utils;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.*;

public class SynGenerateSchema {

    public static SynGenerateSchema load(String name){
        Yaml yml = new Yaml(new Constructor(SynGenerateSchema.class));
        InputStream inputStream = SynGenerateSchema.class.getClassLoader()
                .getResourceAsStream("syn-schema.yml");
        List<String> datasets = new ArrayList<>();
        for(Object obj : yml.loadAll(inputStream)){
            if(obj instanceof SynGenerateSchema){
                SynGenerateSchema g = (SynGenerateSchema) obj;
                if(g.dataset.equals(name)) return g;
                datasets.add(g.dataset);
//                System.out.println(JSON.toJSONString(g));
            }
        }
        throw new IllegalArgumentException("dataset not found, available: "+String.join(",", datasets)+" got "+name);
    }

    private String dataset;
    private int start, end;
    private boolean repeat;
    private PropertySchema node;
    private PropertySchema rel;

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

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public static class PropertySchema{
        private String id;
        private List<String> type;
        private List<String> name;
        private List<Float> update;
        private Integer step;
        private Integer delay;
        private Integer cnt;
        private Float eupdate;

        public PropertySchema(){}

        public PropertySchema(String line){

        }

        public List<String> getName() {
            return name;
        }

        public void setName(List<String> name) {
            this.name = name;
        }

        public List<String> getType() {
            return type;
        }

        public List<PVal.Type> getPValType() {
            ArrayList<PVal.Type> lst = new ArrayList<>();
            PVal.Type t;
            for(String type : getType()) {
                switch (type) {
                    case "int":
                        t = PVal.Type.INT;
                        break;
                    case "str":
                        t = PVal.Type.STRING;
                        break;
                    case "float":
                        t = PVal.Type.FLOAT;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown type: " + type);
                }
                lst.add(t);
            }
            return lst;
        }

        public void setType(List<String> type) {
            this.type = type;
        }

        public List<Float> getUpdate() {
            return update;
        }

        public void setUpdate(List<Float> update) {
            this.update = update;
        }

        public Integer getDelay() {
            return delay;
        }

        public void setDelay(Integer delay) {
            this.delay = delay;
        }

        public Integer getCnt() {
            return cnt;
        }

        public void setCnt(Integer cnt) {
            this.cnt = cnt;
        }

        public Integer getStep() {
            return step;
        }

        public void setStep(Integer step) {
            this.step = step;
        }

        public Float getEupdate() {
            return eupdate;
        }

        public void setEupdate(Float eupdate) {
            this.eupdate = eupdate;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static List<SynGenerateSchema> loadAllTest(){
        Yaml yml = new Yaml(new Constructor(GenPropTestSchema.class));
        InputStream inputStream = SynGenerateSchema.class.getClassLoader().getResourceAsStream("syn-test.yml");
        List<SynGenerateSchema> all = new ArrayList<>();
        PropertySchema epg = new PropertySchema();
        epg.setCnt(0);
        for(Object obj : yml.loadAll(inputStream)){
            if(obj instanceof GenPropTestSchema){
                GenPropTestSchema pg = (GenPropTestSchema) obj;
                SynGenerateSchema g = new SynGenerateSchema();
                g.setStart(1);
                g.setEnd(2000_0000);
                g.setRepeat(true);
                g.setNode(pg.conv());
                g.setRel(epg);
                all.add(g);
            }
        }
        return all;
    }

    public static class GenPropTestSchema{
        public List<PVal.Type> getPValType() {
            ArrayList<PVal.Type> lst = new ArrayList<>();
            PVal.Type t;
            for(int i=0; i<getType().length(); i++) {
                char type = getType().charAt(i);
                switch (type) {
                    case 'i':
                        t = PVal.Type.INT;
                        break;
                    case 'f':
                        t = PVal.Type.FLOAT;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown type: " + type);
                }
                lst.add(t);
            }
            return lst;
        }

        public List<String> getName() {
            String tp = getType();
            ArrayList<String> arr = new ArrayList<>();
            for(int i=0; i<tp.length(); i++) {
                char type = tp.charAt(i);
                arr.add(type+""+(i+1));
            }
            return arr;
        }

        public PropertySchema conv(){
            PropertySchema p = new PropertySchema();
            p.setName(this.getName());
            p.setCnt(this.getCnt());
            p.setDelay(this.getDelay());
            p.setEupdate(this.getEupdate());
            p.setUpdate(this.getUpdate());
            p.setStep(this.getStep());
            p.setType(this.getTypeStr());
            p.setId(this.getId());
            return p;
        }

        private List<String> getTypeStr() {
            String tp = getType();
            ArrayList<String> arr = new ArrayList<>();
            for(int i=0; i<tp.length(); i++) {
                char type = tp.charAt(i);
                arr.add(type=='i'?"int":"float");
            }
            return arr;
        }

        private String id;
        private String type;
        private List<Float> update;
        private boolean repeat;
        private Integer step;
        private Integer delay;
        private Integer cnt;
        private Float eupdate;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Float> getUpdate() {
            return update;
        }

        public void setUpdate(List<Float> update) {
            this.update = update;
        }

        public Integer getDelay() {
            return delay;
        }

        public void setDelay(Integer delay) {
            this.delay = delay;
        }

        public Integer getCnt() {
            return cnt;
        }

        public void setCnt(Integer cnt) {
            this.cnt = cnt;
        }

        public Integer getStep() {
            return step;
        }

        public void setStep(Integer step) {
            this.step = step;
        }

        public Float getEupdate() {
            return eupdate;
        }

        public void setEupdate(Float eupdate) {
            this.eupdate = eupdate;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isRepeat() {
            return repeat;
        }

        public void setRepeat(boolean repeat) {
            this.repeat = repeat;
        }
    }
}
