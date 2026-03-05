package com.patriot.nav.routing;

import java.util.Map;

public class CompressedGraph {

    private final int[] lat;
    private final int[] lon;
    private final int[] firstEdge;

    private final int[] edgeTo;
    private final int[] edgeNext;

    private final float[] weight;          // travel time in seconds
    private final int[] flags;             // access flags
    private final byte[] highwayType;

    // Neue Felder
    private final float[] distance;        // Meter
    private final float[] speed;           // km/h
    private final String[] wayType;        // OSM highway type
    private final Map<String, String>[] tags; // OSM tags
    private final boolean[] oneWay;        // true = forward only

    public CompressedGraph(
            int[] lat,
            int[] lon,
            int[] firstEdge,
            int[] edgeTo,
            int[] edgeNext,
            float[] weight,
            int[] flags,
            byte[] highwayType,
            float[] distance,
            float[] speed,
            String[] wayType,
            Map<String, String>[] tags,
            boolean[] oneWay
    ) {
        this.lat = lat;
        this.lon = lon;
        this.firstEdge = firstEdge;
        this.edgeTo = edgeTo;
        this.edgeNext = edgeNext;
        this.weight = weight;
        this.flags = flags;
        this.highwayType = highwayType;

        this.distance = distance;
        this.speed = speed;
        this.wayType = wayType;
        this.tags = tags;
        this.oneWay = oneWay;
    }

    public int nodeCount() { return lat.length; }

    public double lat(int node) { return lat[node] / 1e6; }
    public double lon(int node) { return lon[node] / 1e6; }

    public int firstEdge(int node) { return firstEdge[node]; }
    public int edgeTo(int edge) { return edgeTo[edge]; }
    public int edgeNext(int edge) { return edgeNext[edge]; }

    public float weight(int edge) { return weight[edge]; }
    public int flags(int edge) { return flags[edge]; }
    public byte highwayType(int edge) { return highwayType[edge]; }

    // Neue Getter
    public float distance(int edge) { return distance[edge]; }
    public float speed(int edge) { return speed[edge]; }
    public String wayType(int edge) { return wayType[edge]; }
    public Map<String, String> tags(int edge) { return tags[edge]; }
    public boolean oneWay(int edge) { return oneWay[edge]; }

    public void saveToFile(java.io.File f) throws java.io.IOException {
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(f)))) {
            out.writeInt(lat.length);

            writeIntArray(out, lat);
            writeIntArray(out, lon);
            writeIntArray(out, firstEdge);

            writeIntArray(out, edgeTo);
            writeIntArray(out, edgeNext);

            writeFloatArray(out, weight);
            writeIntArray(out, flags);

            out.writeInt(highwayType.length);
            out.write(highwayType);

            writeFloatArray(out, distance);
            writeFloatArray(out, speed);

            // wayType: write length then each string
            out.writeInt(wayType.length);
            for (String s : wayType) out.writeUTF(s == null ? "" : s);

            out.writeInt(tags.length);
            for (Map<String,String> m : tags) {
                if (m == null) { out.writeInt(0); continue; }
                out.writeInt(m.size());
                for (var e : m.entrySet()) {
                    out.writeUTF(e.getKey());
                    out.writeUTF(e.getValue());
                }
            }

            out.writeInt(oneWay.length);
            for (boolean b : oneWay) out.writeBoolean(b);
        }
    }

    public static CompressedGraph loadFromFile(java.io.File f) throws java.io.IOException {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f)))) {
            in.readInt();

            int[] lat = readIntArray(in);
            int[] lon = readIntArray(in);
            int[] firstEdge = readIntArray(in);

            int[] edgeTo = readIntArray(in);
            int[] edgeNext = readIntArray(in);

            float[] weight = readFloatArray(in);
            int[] flags = readIntArray(in);

            int hwLen = in.readInt();
            byte[] highwayType = new byte[hwLen];
            in.readFully(highwayType);

            float[] distance = readFloatArray(in);
            float[] speed = readFloatArray(in);

            int wtLen = in.readInt();
            String[] wayType = new String[wtLen];
            for (int i = 0; i < wtLen; i++) {
                String s = in.readUTF();
                wayType[i] = s.isEmpty() ? null : s;
            }

            int tagsLen = in.readInt();
            @SuppressWarnings("unchecked")
            Map<String,String>[] tags = (Map<String,String>[]) new Map[tagsLen];
            for (int i = 0; i < tagsLen; i++) {
                int size = in.readInt();
                if (size == 0) { tags[i] = null; continue; }
                Map<String,String> m = new java.util.HashMap<>();
                for (int j = 0; j < size; j++) {
                    String k = in.readUTF();
                    String v = in.readUTF();
                    m.put(k, v);
                }
                tags[i] = m;
            }

            int oneLen = in.readInt();
            boolean[] oneWay = new boolean[oneLen];
            for (int i = 0; i < oneLen; i++) oneWay[i] = in.readBoolean();

            return new CompressedGraph(lat, lon, firstEdge, edgeTo, edgeNext, weight, flags, highwayType, distance, speed, wayType, tags, oneWay);
        }
    }

    private static void writeIntArray(java.io.DataOutputStream out, int[] a) throws java.io.IOException {
        out.writeInt(a.length);
        for (int v : a) out.writeInt(v);
    }
    private static int[] readIntArray(java.io.DataInputStream in) throws java.io.IOException {
        int l = in.readInt();
        int[] a = new int[l];
        for (int i = 0; i < l; i++) a[i] = in.readInt();
        return a;
    }
    private static void writeFloatArray(java.io.DataOutputStream out, float[] a) throws java.io.IOException {
        out.writeInt(a.length);
        for (float v : a) out.writeFloat(v);
    }
    private static float[] readFloatArray(java.io.DataInputStream in) throws java.io.IOException {
        int l = in.readInt();
        float[] a = new float[l];
        for (int i = 0; i < l; i++) a[i] = in.readFloat();
        return a;
    }
}
