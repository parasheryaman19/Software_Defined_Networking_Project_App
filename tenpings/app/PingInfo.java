package org.tenpings.app;

import org.onosproject.net.HostId;

import java.text.DateFormat;
import java.util.Date;

public class PingInfo {
    protected HostId src;
    protected HostId dst;
    protected Date date;

    public PingInfo(HostId s, HostId d) {
        this.src = s;
        this.dst = d;
        this.date = new Date();
    }

    public String toString() {
        String info = "";

        info = "--- " + date + " from " + src.mac().toString() + " to " + dst.mac().toString();

        return info;
    }
}
