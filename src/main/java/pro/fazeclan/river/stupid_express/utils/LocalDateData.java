package pro.fazeclan.river.stupid_express.utils;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;

public class LocalDateData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int day;
    private int month;
    private int year;
    
    public LocalDateData() {
        // 使用系统默认时区
        LocalDate today = LocalDate.now();
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }
    
    // 指定时区
    public LocalDateData(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }
    
    // 指定时区字符串
    public LocalDateData(String timezone) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }
    
    // getters
    public int getDay() { return day; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
}