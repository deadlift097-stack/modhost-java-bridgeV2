package net.fabricmc.loader.api;

public interface Version extends Comparable<Version> {
    String getFriendlyString();

    static Version parse(String s) {
        final String v = s == null ? "" : s;
        return new Version() {
            public String getFriendlyString() { return v; }
            public int compareTo(Version o) { return compareVersions(v, o.getFriendlyString()); }
            @Override public String toString() { return v; }
        };
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("[.+\\-]"), y = b.split("[.+\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0", q = i < y.length ? y[i] : "0";
            try {
                int c = Integer.compare(Integer.parseInt(p), Integer.parseInt(q));
                if (c != 0) return c;
            } catch (NumberFormatException e) {
                int c = p.compareTo(q);
                if (c != 0) return c;
            }
        }
        return 0;
    }
}
