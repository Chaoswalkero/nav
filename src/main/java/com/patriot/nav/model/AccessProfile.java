package com.patriot.nav.model;

import lombok.Data;

@Data
public class AccessProfile {
    private boolean foot;
    private boolean bicycle;
    private boolean motorVehicle;
    private boolean hgv;
    private boolean bus;
    private boolean taxi;
    private boolean emergency;
    private boolean delivery;
    private boolean agricultural;
}
