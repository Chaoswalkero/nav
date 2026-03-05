package com.patriot.nav.routing;

public final class EdgeFlags {

    // Access bits
    public static final int ACCESS_FOOT          = 1 << 0;
    public static final int ACCESS_BICYCLE       = 1 << 1;
    public static final int ACCESS_MOTOR_VEHICLE = 1 << 2;
    public static final int ACCESS_HGV           = 1 << 3;
    public static final int ACCESS_BUS           = 1 << 4;
    public static final int ACCESS_TAXI          = 1 << 5;
    public static final int ACCESS_EMERGENCY     = 1 << 6;
    public static final int ACCESS_DELIVERY      = 1 << 7;
    public static final int ACCESS_AGRICULTURAL  = 1 << 8;

    // Direction bits
    public static final int DIR_FORWARD          = 1 << 9;
    public static final int DIR_BACKWARD         = 1 << 10;

    // Surface / special bits (examples)
    public static final int SURFACE_UNPAVED      = 1 << 11;
    public static final int SURFACE_TRACK        = 1 << 12;
    public static final int SURFACE_STEPS        = 1 << 13;

    private EdgeFlags() {}
}
