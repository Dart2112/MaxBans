package net.lapismc.maxbans.geoip;

public class GeoIP implements Comparable<GeoIP> {
    private long value;
    private String country;

    public GeoIP(final long value, final String country) {
        super();
        this.value = value;
        this.country = country;
    }

    String getCountry() {
        return this.country;
    }

    public int compareTo(final GeoIP o) {
        return Long.compare(this.value, o.value);
    }

}
